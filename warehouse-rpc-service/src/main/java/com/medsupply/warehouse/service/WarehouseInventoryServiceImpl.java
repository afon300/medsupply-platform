package com.medsupply.warehouse.service;

import com.medsupply.warehouse.entity.*;
import com.medsupply.warehouse.exception.InsufficientWarehouseStockException;
import com.medsupply.warehouse.exception.ReservationNotFoundException;
import com.medsupply.warehouse.exception.SupplyItemNotFoundException;
import com.medsupply.warehouse.service.model.CatalogSummaryView;
import com.medsupply.warehouse.service.model.ReservationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class WarehouseInventoryServiceImpl implements WarehouseInventoryService {

    private static final Logger logger = LoggerFactory.getLogger(WarehouseInventoryServiceImpl.class);

    private final MedicalSupplyItemRepository supplyItemRepository;
    private final InventoryBatchRepository batchRepository;
    private final StockReservationRepository reservationRepository;

    @Autowired
    public WarehouseInventoryServiceImpl(
            MedicalSupplyItemRepository supplyItemRepository,
            InventoryBatchRepository batchRepository,
            StockReservationRepository reservationRepository) {
        this.supplyItemRepository = supplyItemRepository;
        this.batchRepository = batchRepository;
        this.reservationRepository = reservationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogSummaryView> listCatalog(String categoryFilter, String searchQuery) {
        String normalizedCategory = categoryFilter == null ? "" : categoryFilter.trim().toUpperCase(Locale.ROOT);
        String normalizedQuery = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);

        List<MedicalSupplyItem> allItems = supplyItemRepository.findAll();
        List<CatalogSummaryView> views = new ArrayList<>();

        for (MedicalSupplyItem item : allItems) {
            if (!normalizedCategory.isEmpty() && !normalizedCategory.equals("ALL")
                    && !item.getCategory().name().equals(normalizedCategory)) {
                continue;
            }
            if (!normalizedQuery.isEmpty()
                    && !item.getName().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    && !item.getSku().toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                continue;
            }
            views.add(buildSummary(item));
        }

        views.sort(Comparator.comparing(CatalogSummaryView::sku));
        logger.info("Catalog query executed (category='{}', query='{}') -> {} items returned",
                normalizedCategory, normalizedQuery, views.size());
        return views;
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogSummaryView getCatalogSummaryBySku(String sku) {
        MedicalSupplyItem item = supplyItemRepository.findBySku(sku)
                .orElseThrow(() -> new SupplyItemNotFoundException(sku));
        return buildSummary(item);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryBatch> getBatchesForSku(String sku) {
        if (supplyItemRepository.findBySku(sku).isEmpty()) {
            throw new SupplyItemNotFoundException(sku);
        }
        return batchRepository.findBySupplyItemSkuOrderByExpiryDateAsc(sku);
    }

    @Override
    @Transactional
    public ReservationOutcome reserveStock(
            String idempotencyKey,
            String hospitalId,
            String departmentCode,
            String externalOrderRef,
            boolean priorityEmergency,
            Map<String, Integer> requestedLines) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is mandatory for stock reservation");
        }
        if (requestedLines == null || requestedLines.isEmpty()) {
            throw new IllegalArgumentException("At least one SKU line item is required for stock reservation");
        }

        Optional<StockReservation> existing = reservationRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            logger.info("Idempotent replay hit for key '{}' -> returning existing reservation '{}'",
                    idempotencyKey, existing.get().getReservationId());
            return new ReservationOutcome(existing.get(), true);
        }

        String reservationId = "RSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        StockReservation reservation = new StockReservation(
                reservationId,
                idempotencyKey,
                hospitalId,
                departmentCode,
                externalOrderRef,
                priorityEmergency,
                BigDecimal.ZERO,
                Instant.now());

        BigDecimal runningTotal = BigDecimal.ZERO;

        for (Map.Entry<String, Integer> entry : requestedLines.entrySet()) {
            String sku = entry.getKey();
            int requestedQty = entry.getValue() == null ? 0 : entry.getValue();
            if (requestedQty <= 0) {
                throw new IllegalArgumentException("Requested quantity must be > 0 for SKU " + sku);
            }

            MedicalSupplyItem item = supplyItemRepository.findBySku(sku)
                    .orElseThrow(() -> new SupplyItemNotFoundException(sku));

            // FEFO (First-Expired, First-Out) allocation across non-expired batches
            List<InventoryBatch> activeBatches = batchRepository
                    .findBySupplyItemSkuAndAvailableUnitsGreaterThanOrderByExpiryDateAsc(sku, 0);

            int totalAvailable = activeBatches.stream().mapToInt(InventoryBatch::getAvailableUnits).sum();
            if (totalAvailable < requestedQty) {
                logger.warn("Stock shortage on SKU {}: requested={}, available={}", sku, requestedQty, totalAvailable);
                throw new InsufficientWarehouseStockException(sku, requestedQty, totalAvailable);
            }

            int remainingToAllocate = requestedQty;
            for (InventoryBatch batch : activeBatches) {
                if (remainingToAllocate == 0) {
                    break;
                }
                int takeFromBatch = Math.min(remainingToAllocate, batch.getAvailableUnits());
                batch.deductUnits(takeFromBatch);
                batchRepository.save(batch);

                BigDecimal lineCost = item.getUnitPriceEur().multiply(BigDecimal.valueOf(takeFromBatch));
                runningTotal = runningTotal.add(lineCost);

                StockReservationLine line = new StockReservationLine(
                        batch,
                        item.getSku(),
                        item.getName(),
                        batch.getBatchLotNumber(),
                        batch.getExpiryDate(),
                        item.getStorageCondition(),
                        takeFromBatch,
                        item.getUnitPriceEur(),
                        lineCost);
                reservation.addLine(line);
                remainingToAllocate -= takeFromBatch;

                logger.info("Allocated {} units of {} from batch {} (exp: {}, remaining batch units: {})",
                        takeFromBatch, sku, batch.getBatchLotNumber(), batch.getExpiryDate(), batch.getAvailableUnits());
            }
        }

        reservation.setTotalReservedCostEur(runningTotal);
        StockReservation persisted = reservationRepository.save(reservation);

        logger.info("Confirmed warehouse reservation {} for hospital={} dept={} orderRef={} total={} EUR",
                reservationId, hospitalId, departmentCode, externalOrderRef, runningTotal);
        return new ReservationOutcome(persisted, false);
    }

    @Override
    @Transactional
    public int releaseReservation(String reservationId, String reason) {
        StockReservation reservation = reservationRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            logger.info("Reservation {} is already RELEASED; idempotent release acknowledged", reservationId);
            return 0;
        }

        int restoredUnits = 0;
        for (StockReservationLine line : reservation.getLines()) {
            InventoryBatch batch = line.getBatch();
            batch.restoreUnits(line.getAllocatedQuantity());
            batchRepository.save(batch);
            restoredUnits += line.getAllocatedQuantity();
        }

        reservation.markReleased(reason == null ? "Order cancelled" : reason, Instant.now());
        reservationRepository.save(reservation);

        logger.info("Released reservation {} (reason='{}') -> restored {} total units across {} batch allocations",
                reservationId, reason, restoredUnits, reservation.getLines().size());
        return restoredUnits;
    }

    @Override
    @Transactional(readOnly = true)
    public StockReservation getReservation(String reservationId) {
        return reservationRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

    private CatalogSummaryView buildSummary(MedicalSupplyItem item) {
        List<InventoryBatch> batches = batchRepository.findBySupplyItemSkuOrderByExpiryDateAsc(item.getSku());
        int totalUnits = 0;
        int activeCount = 0;
        LocalDate earliestExpiry = null;

        for (InventoryBatch batch : batches) {
            if (batch.getAvailableUnits() > 0) {
                totalUnits += batch.getAvailableUnits();
                activeCount++;
                if (earliestExpiry == null || batch.getExpiryDate().isBefore(earliestExpiry)) {
                    earliestExpiry = batch.getExpiryDate();
                }
            }
        }

        return new CatalogSummaryView(
                item.getSku(),
                item.getName(),
                item.getCategory().name(),
                item.getStorageCondition().name(),
                item.getUnitOfMeasure(),
                item.getUnitPriceEur(),
                totalUnits,
                activeCount,
                earliestExpiry != null ? earliestExpiry.toString() : "OUT_OF_STOCK",
                item.getStorageCondition().isColdChainRequired());
    }
}
