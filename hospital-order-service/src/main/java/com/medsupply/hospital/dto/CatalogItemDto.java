package com.medsupply.hospital.dto;

import java.math.BigDecimal;
import java.util.List;

public record CatalogItemDto(
        String sku,
        String name,
        String category,
        String storageCondition,
        String unitOfMeasure,
        BigDecimal unitPriceEur,
        int totalAvailableUnits,
        int activeBatchCount,
        String earliestExpiryDate,
        boolean coldChainRequired,
        List<BatchLotDto> batches) {

    public record BatchLotDto(
            String batchLotNumber,
            int availableUnits,
            String expiryDate,
            String warehouseZone) {
    }
}
