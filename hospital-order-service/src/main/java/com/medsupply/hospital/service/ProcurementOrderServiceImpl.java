package com.medsupply.hospital.service;

import com.medsupply.hospital.dto.*;
import com.medsupply.hospital.entity.*;
import com.medsupply.hospital.exception.InsufficientBudgetException;
import com.medsupply.hospital.exception.InvalidOrderStateException;
import com.medsupply.hospital.exception.ResourceNotFoundException;
import com.medsupply.rpc.warehouse.BatchAllocation;
import com.medsupply.rpc.warehouse.ReserveStockResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
public class ProcurementOrderServiceImpl implements ProcurementOrderService {

    private static final Logger logger = LoggerFactory.getLogger(ProcurementOrderServiceImpl.class);

    private final ProcurementOrderRepository orderRepository;
    private final HospitalDepartmentRepository departmentRepository;
    private final WarehouseRpcGateway warehouseGateway;
    private final TransactionTemplate transactionTemplate;
    private final String hospitalId;

    @Autowired
    public ProcurementOrderServiceImpl(
            ProcurementOrderRepository orderRepository,
            HospitalDepartmentRepository departmentRepository,
            WarehouseRpcGateway warehouseGateway,
            TransactionTemplate transactionTemplate,
            @Value("${medsupply.hospital.id:APHP-SAINT-LOUIS-PARIS}") String hospitalId) {
        this.orderRepository = orderRepository;
        this.departmentRepository = departmentRepository;
        this.warehouseGateway = warehouseGateway;
        this.transactionTemplate = transactionTemplate;
        this.hospitalId = hospitalId;
    }

    @Override
    public OrderResponseDto createOrder(CreateOrderRequest request, String headerIdempotencyKey) {
        String effectiveIdempotencyKey = resolveIdempotencyKey(request.idempotencyKey(), headerIdempotencyKey);

        // 1. Check local idempotency ledger before crossing network boundary
        Optional<ProcurementOrder> existingOrder = orderRepository.findByIdempotencyKey(effectiveIdempotencyKey);
        if (existingOrder.isPresent()) {
            logger.info("Idempotent REST order replay detected for key '{}' -> returning {}",
                    effectiveIdempotencyKey, existingOrder.get().getOrderReference());
            return toOrderDto(existingOrder.get(), true);
        }

        // 2. Validate priority enum and department existence prior to remote RPC call
        OrderPriority priority = parsePriority(request.priority());
        HospitalDepartment deptSnapshot = departmentRepository.findByDepartmentCode(request.departmentCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Hospital department not found: " + request.departmentCode()));

        Map<String, Integer> aggregatedLines = new LinkedHashMap<>();
        for (CreateOrderRequest.OrderLineRequest item : request.items()) {
            aggregatedLines.merge(item.sku().trim().toUpperCase(Locale.ROOT), item.quantity(), Integer::sum);
        }

        String orderReference = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        boolean emergencyPriority = priority == OrderPriority.EMERGENCY_COLD_CHAIN || priority == OrderPriority.URGENT;

        // 3. Synchronous RPC call to Central Regional Warehouse (kept outside local DB transaction lock!)
        ReserveStockResponse rpcReservation = warehouseGateway.reserveWarehouseStock(
                effectiveIdempotencyKey,
                hospitalId,
                deptSnapshot.getDepartmentCode(),
                orderReference,
                emergencyPriority,
                aggregatedLines);

        BigDecimal reservedTotal = BigDecimal.valueOf(rpcReservation.getTotalReservedCostEur())
                .setScale(2, RoundingMode.HALF_UP);

        // 4. Local transactional commit of order & department budget; execute Saga compensation on failure!
        try {
            ProcurementOrder saved = transactionTemplate.execute(status -> {
                HospitalDepartment department = departmentRepository
                        .findByDepartmentCode(request.departmentCode())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Hospital department not found: " + request.departmentCode()));

                if (department.getRemainingBudgetEur().compareTo(reservedTotal) < 0) {
                    throw new InsufficientBudgetException(
                            department.getDepartmentCode(),
                            reservedTotal,
                            department.getRemainingBudgetEur());
                }

                department.commitBudget(reservedTotal);
                departmentRepository.save(department);

                ProcurementOrder order = new ProcurementOrder(
                        orderReference,
                        effectiveIdempotencyKey,
                        hospitalId,
                        department,
                        request.requestedByClinician().trim(),
                        priority,
                        rpcReservation.getReservationId(),
                        reservedTotal,
                        request.clinicalNotes() != null ? request.clinicalNotes().trim() : "",
                        Instant.now());

                for (BatchAllocation alloc : rpcReservation.getAllocationsList()) {
                    order.addLineItem(new OrderLineItem(
                            alloc.getSku(),
                            alloc.getItemName(),
                            alloc.getBatchLotNumber(),
                            alloc.getExpiryDate(),
                            alloc.getStorageCondition(),
                            alloc.getAllocatedQuantity(),
                            BigDecimal.valueOf(alloc.getUnitPriceEur()).setScale(2, RoundingMode.HALF_UP),
                            BigDecimal.valueOf(alloc.getLineTotalEur()).setScale(2, RoundingMode.HALF_UP)));
                }

                return orderRepository.save(order);
            });

            logger.info("Procurement order {} committed (warehouseReservation={}, total={} EUR, dept={})",
                    orderReference, rpcReservation.getReservationId(), reservedTotal, request.departmentCode());
            return toOrderDto(Objects.requireNonNull(saved), rpcReservation.getReplayedFromIdempotency());
        } catch (RuntimeException localEx) {
            logger.warn("Local order transaction rejected ({}). Triggering Saga compensating ReleaseReservation RPC for {}",
                    localEx.getMessage(), rpcReservation.getReservationId());
            try {
                warehouseGateway.releaseWarehouseStock(
                        rpcReservation.getReservationId(),
                        "Saga compensation: " + localEx.getClass().getSimpleName());
            } catch (Exception compensationEx) {
                logger.error("CRITICAL: Compensating ReleaseReservation failed for reservation {}: {}",
                        rpcReservation.getReservationId(), compensationEx.getMessage(), compensationEx);
            }
            throw localEx;
        }
    }

    @Override
    public List<OrderResponseDto> listOrders(String departmentCode, String statusFilter) {
        List<ProcurementOrder> orders = (departmentCode != null && !departmentCode.isBlank())
                ? orderRepository.findByDepartmentDepartmentCodeOrderByCreatedAtDesc(departmentCode.trim())
                : orderRepository.findAllByOrderByCreatedAtDesc();

        String normalizedStatus = statusFilter == null ? "" : statusFilter.trim().toUpperCase(Locale.ROOT);
        return orders.stream()
                .filter(o -> normalizedStatus.isEmpty() || normalizedStatus.equals("ALL")
                        || o.getStatus().name().equals(normalizedStatus))
                .map(o -> toOrderDto(o, false))
                .toList();
    }

    @Override
    public OrderResponseDto getOrderByReference(String orderReference) {
        ProcurementOrder order = orderRepository.findByOrderReference(orderReference)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Procurement order not found: " + orderReference));
        return toOrderDto(order, false);
    }

    @Override
    public OrderResponseDto cancelOrder(String orderReference, String reason) {
        ProcurementOrder existing = orderRepository.findByOrderReference(orderReference)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Procurement order not found: " + orderReference));

        if (existing.getStatus() == OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException(
                    "Procurement order " + orderReference + " is already CANCELLED");
        }

        // 1. Invoke remote gRPC compensating stock release first
        warehouseGateway.releaseWarehouseStock(
                existing.getWarehouseReservationId(),
                reason != null && !reason.isBlank() ? reason : "Cancelled by hospital clinician");

        // 2. Refund local department committed budget & mark order CANCELLED
        ProcurementOrder updated = transactionTemplate.execute(status -> {
            ProcurementOrder managedOrder = orderRepository.findByOrderReference(orderReference)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Procurement order not found: " + orderReference));
            HospitalDepartment department = managedOrder.getDepartment();
            department.refundBudget(managedOrder.getTotalAmountEur());
            departmentRepository.save(department);

            managedOrder.markCancelled(Instant.now());
            return orderRepository.save(managedOrder);
        });

        logger.info("Cancelled procurement order {} and released warehouse reservation {}",
                orderReference, existing.getWarehouseReservationId());
        return toOrderDto(Objects.requireNonNull(updated), false);
    }

    @Override
    public PlatformMetricsDto getPlatformMetrics() {
        List<HospitalDepartment> departments = departmentRepository.findAll();
        List<ProcurementOrder> orders = orderRepository.findAll();

        BigDecimal committedSpend = departments.stream()
                .map(HospitalDepartment::getCommittedSpendEur)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainingBudget = departments.stream()
                .map(HospitalDepartment::getRemainingBudgetEur)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int confirmedCount = (int) orders.stream().filter(o -> o.getStatus() == OrderStatus.CONFIRMED).count();
        int cancelledCount = (int) orders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();

        try {
            WarehouseRpcGateway.CatalogSnapshot snapshot = warehouseGateway.fetchCatalog("", "");
            int totalUnits = snapshot.items().stream().mapToInt(CatalogItemDto::totalAvailableUnits).sum();
            int coldChainCount = (int) snapshot.items().stream().filter(CatalogItemDto::coldChainRequired).count();

            return new PlatformMetricsDto(
                    hospitalId,
                    snapshot.warehouseNodeId(),
                    snapshot.items().size(),
                    totalUnits,
                    coldChainCount,
                    confirmedCount,
                    cancelledCount,
                    committedSpend,
                    remainingBudget,
                    true);
        } catch (Exception rpcEx) {
            logger.warn("Metrics summary degraded: warehouse gRPC unavailable ({})", rpcEx.getMessage());
            return new PlatformMetricsDto(
                    hospitalId,
                    "UNREACHABLE",
                    0,
                    0,
                    0,
                    confirmedCount,
                    cancelledCount,
                    committedSpend,
                    remainingBudget,
                    false);
        }
    }

    private String resolveIdempotencyKey(String bodyKey, String headerKey) {
        if (headerKey != null && !headerKey.isBlank()) {
            return headerKey.trim();
        }
        if (bodyKey != null && !bodyKey.isBlank()) {
            return bodyKey.trim();
        }
        return "AUTO-" + UUID.randomUUID();
    }

    private OrderPriority parsePriority(String raw) {
        try {
            return OrderPriority.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Invalid priority '" + raw + "'. Allowed values: STANDARD, URGENT, EMERGENCY_COLD_CHAIN");
        }
    }

    private OrderResponseDto toOrderDto(ProcurementOrder order, boolean replayed) {
        List<OrderResponseDto.AllocatedLineDto> lines = order.getLineItems().stream()
                .map(line -> new OrderResponseDto.AllocatedLineDto(
                        line.getSku(),
                        line.getItemName(),
                        line.getBatchLotNumber(),
                        line.getExpiryDate(),
                        line.getStorageCondition(),
                        line.getAllocatedQuantity(),
                        line.getUnitPriceEur(),
                        line.getLineTotalEur()))
                .toList();

        return new OrderResponseDto(
                order.getOrderReference(),
                order.getIdempotencyKey(),
                order.getHospitalId(),
                order.getDepartment().getDepartmentCode(),
                order.getDepartment().getName(),
                order.getRequestedByClinician(),
                order.getPriority().name(),
                order.getStatus().name(),
                order.getWarehouseReservationId(),
                order.getTotalAmountEur(),
                order.getClinicalNotes(),
                order.getCreatedAt().toString(),
                order.getCancelledAt() != null ? order.getCancelledAt().toString() : null,
                replayed,
                lines);
    }
}
