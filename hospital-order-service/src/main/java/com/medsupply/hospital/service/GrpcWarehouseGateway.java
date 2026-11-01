package com.medsupply.hospital.service;

import com.medsupply.hospital.dto.CatalogItemDto;
import com.medsupply.hospital.exception.ResourceNotFoundException;
import com.medsupply.hospital.exception.UpstreamRpcUnavailableException;
import com.medsupply.hospital.exception.WarehouseStockConflictException;
import com.medsupply.rpc.warehouse.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class GrpcWarehouseGateway implements WarehouseRpcGateway {

    private static final Logger logger = LoggerFactory.getLogger(GrpcWarehouseGateway.class);

    @GrpcClient("warehouseService")
    private WarehouseFulfillmentServiceGrpc.WarehouseFulfillmentServiceBlockingStub warehouseStub;

    private final long rpcDeadlineMs;

    public GrpcWarehouseGateway(
            @Value("${medsupply.hospital.rpc-deadline-ms:3500}") long rpcDeadlineMs) {
        this.rpcDeadlineMs = rpcDeadlineMs;
    }

    @Override
    public CatalogSnapshot fetchCatalog(String categoryFilter, String searchQuery) {
        try {
            ListCatalogRequest request = ListCatalogRequest.newBuilder()
                    .setCategoryFilter(categoryFilter == null ? "" : categoryFilter)
                    .setSearchQuery(searchQuery == null ? "" : searchQuery)
                    .build();

            ListCatalogResponse response = stubWithDeadline().listCatalog(request);
            List<CatalogItemDto> items = new ArrayList<>();
            for (SupplyCatalogItem protoItem : response.getItemsList()) {
                items.add(toCatalogDto(protoItem, List.of()));
            }
            logger.info("Fetched {} SKUs from central warehouse gRPC node '{}'",
                    items.size(), response.getWarehouseNodeId());
            return new CatalogSnapshot(response.getWarehouseNodeId(), items);
        } catch (StatusRuntimeException ex) {
            throw translateGrpcException("ListCatalog", ex);
        }
    }

    @Override
    public CatalogItemDto fetchItemWithBatches(String sku) {
        try {
            ItemAvailabilityResponse response = stubWithDeadline().getItemAvailability(
                    ItemAvailabilityRequest.newBuilder().setSku(sku).build());

            List<CatalogItemDto.BatchLotDto> batches = response.getBatchesList().stream()
                    .map(b -> new CatalogItemDto.BatchLotDto(
                            b.getBatchLotNumber(),
                            b.getAvailableUnits(),
                            b.getExpiryDate(),
                            b.getWarehouseZone()))
                    .toList();

            return toCatalogDto(response.getItem(), batches);
        } catch (StatusRuntimeException ex) {
            throw translateGrpcException("GetItemAvailability", ex);
        }
    }

    @Override
    public ReserveStockResponse reserveWarehouseStock(
            String idempotencyKey,
            String hospitalId,
            String departmentCode,
            String orderReference,
            boolean emergencyPriority,
            Map<String, Integer> requestedSkuQuantities) {
        try {
            ReserveStockRequest.Builder builder = ReserveStockRequest.newBuilder()
                    .setIdempotencyKey(idempotencyKey)
                    .setHospitalId(hospitalId)
                    .setDepartmentCode(departmentCode)
                    .setExternalOrderRef(orderReference)
                    .setPriorityEmergency(emergencyPriority);

            for (Map.Entry<String, Integer> entry : requestedSkuQuantities.entrySet()) {
                builder.addLines(ReservationLineRequest.newBuilder()
                        .setSku(entry.getKey())
                        .setRequestedQuantity(entry.getValue())
                        .build());
            }

            logger.info("Invoking gRPC ReserveStock for orderRef={} idempotencyKey={} lines={}",
                    orderReference, idempotencyKey, requestedSkuQuantities.size());
            return stubWithDeadline().reserveStock(builder.build());
        } catch (StatusRuntimeException ex) {
            throw translateGrpcException("ReserveStock", ex);
        }
    }

    @Override
    public ReleaseReservationResponse releaseWarehouseStock(String reservationId, String reason) {
        try {
            logger.info("Invoking compensating gRPC ReleaseReservation for reservationId={} reason='{}'",
                    reservationId, reason);
            return stubWithDeadline().releaseReservation(
                    ReleaseReservationRequest.newBuilder()
                            .setReservationId(reservationId)
                            .setReason(reason == null ? "Hospital cancellation" : reason)
                            .build());
        } catch (StatusRuntimeException ex) {
            throw translateGrpcException("ReleaseReservation", ex);
        }
    }

    private WarehouseFulfillmentServiceGrpc.WarehouseFulfillmentServiceBlockingStub stubWithDeadline() {
        return warehouseStub.withDeadlineAfter(rpcDeadlineMs, TimeUnit.MILLISECONDS);
    }

    private CatalogItemDto toCatalogDto(SupplyCatalogItem item, List<CatalogItemDto.BatchLotDto> batches) {
        return new CatalogItemDto(
                item.getSku(),
                item.getName(),
                item.getCategory(),
                item.getStorageCondition(),
                item.getUnitOfMeasure(),
                BigDecimal.valueOf(item.getUnitPriceEur()).setScale(2, RoundingMode.HALF_UP),
                item.getTotalAvailableUnits(),
                item.getActiveBatchCount(),
                item.getEarliestExpiryDate(),
                item.getColdChainRequired(),
                batches);
    }

    private RuntimeException translateGrpcException(String operation, StatusRuntimeException ex) {
        Status.Code code = ex.getStatus().getCode();
        String description = ex.getStatus().getDescription() != null
                ? ex.getStatus().getDescription()
                : ex.getMessage();

        return switch (code) {
            case NOT_FOUND -> new ResourceNotFoundException(description);
            case FAILED_PRECONDITION -> new WarehouseStockConflictException(description);
            case INVALID_ARGUMENT -> new IllegalArgumentException(description);
            case DEADLINE_EXCEEDED -> new UpstreamRpcUnavailableException(
                    "Central warehouse gRPC operation '" + operation + "' exceeded deadline (" + rpcDeadlineMs + " ms)", ex);
            case UNAVAILABLE -> new UpstreamRpcUnavailableException(
                    "Central warehouse gRPC service is currently unreachable (" + operation + ")", ex);
            default -> new UpstreamRpcUnavailableException(
                    "Warehouse gRPC operation '" + operation + "' failed with status " + code + ": " + description, ex);
        };
    }
}
