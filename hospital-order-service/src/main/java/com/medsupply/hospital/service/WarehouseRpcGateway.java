package com.medsupply.hospital.service;

import com.medsupply.hospital.dto.CatalogItemDto;
import com.medsupply.rpc.warehouse.ReleaseReservationResponse;
import com.medsupply.rpc.warehouse.ReserveStockResponse;

import java.util.List;
import java.util.Map;

public interface WarehouseRpcGateway {

    CatalogSnapshot fetchCatalog(String categoryFilter, String searchQuery);

    CatalogItemDto fetchItemWithBatches(String sku);

    ReserveStockResponse reserveWarehouseStock(
            String idempotencyKey,
            String hospitalId,
            String departmentCode,
            String orderReference,
            boolean emergencyPriority,
            Map<String, Integer> requestedSkuQuantities);

    ReleaseReservationResponse releaseWarehouseStock(String reservationId, String reason);

    record CatalogSnapshot(String warehouseNodeId, List<CatalogItemDto> items) {
    }
}
