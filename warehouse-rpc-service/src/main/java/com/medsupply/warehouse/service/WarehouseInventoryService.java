package com.medsupply.warehouse.service;

import com.medsupply.warehouse.entity.InventoryBatch;
import com.medsupply.warehouse.entity.StockReservation;
import com.medsupply.warehouse.service.model.CatalogSummaryView;
import com.medsupply.warehouse.service.model.ReservationOutcome;

import java.util.List;
import java.util.Map;

public interface WarehouseInventoryService {

    List<CatalogSummaryView> listCatalog(String categoryFilter, String searchQuery);

    CatalogSummaryView getCatalogSummaryBySku(String sku);

    List<InventoryBatch> getBatchesForSku(String sku);

    ReservationOutcome reserveStock(
            String idempotencyKey,
            String hospitalId,
            String departmentCode,
            String externalOrderRef,
            boolean priorityEmergency,
            Map<String, Integer> requestedLines);

    int releaseReservation(String reservationId, String reason);

    StockReservation getReservation(String reservationId);
}
