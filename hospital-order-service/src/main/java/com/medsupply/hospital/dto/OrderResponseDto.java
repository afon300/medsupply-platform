package com.medsupply.hospital.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderResponseDto(
        String orderReference,
        String idempotencyKey,
        String hospitalId,
        String departmentCode,
        String departmentName,
        String requestedByClinician,
        String priority,
        String status,
        String warehouseReservationId,
        BigDecimal totalAmountEur,
        String clinicalNotes,
        String createdAtIso,
        String cancelledAtIso,
        boolean idempotentReplay,
        List<AllocatedLineDto> allocatedBatches) {

    public record AllocatedLineDto(
            String sku,
            String itemName,
            String batchLotNumber,
            String expiryDate,
            String storageCondition,
            int allocatedQuantity,
            BigDecimal unitPriceEur,
            BigDecimal lineTotalEur) {
    }
}
