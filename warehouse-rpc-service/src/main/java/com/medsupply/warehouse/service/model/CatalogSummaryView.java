package com.medsupply.warehouse.service.model;

import java.math.BigDecimal;

public record CatalogSummaryView(
        String sku,
        String name,
        String category,
        String storageCondition,
        String unitOfMeasure,
        BigDecimal unitPriceEur,
        int totalAvailableUnits,
        int activeBatchCount,
        String earliestExpiryDate,
        boolean coldChainRequired) {
}
