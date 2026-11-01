package com.medsupply.hospital.dto;

import java.math.BigDecimal;

public record PlatformMetricsDto(
        String hospitalId,
        String warehouseNodeId,
        int catalogSkuCount,
        int totalWarehouseUnitsAvailable,
        int coldChainSkuCount,
        int confirmedOrdersCount,
        int cancelledOrdersCount,
        BigDecimal totalCommittedSpendEur,
        BigDecimal totalRemainingBudgetEur,
        boolean warehouseRpcHealthy) {
}
