package com.medsupply.hospital.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank(message = "departmentCode is required")
        String departmentCode,

        @NotBlank(message = "requestedByClinician is required")
        @Size(max = 120, message = "requestedByClinician must be at most 120 characters")
        String requestedByClinician,

        @NotBlank(message = "priority is required (STANDARD, URGENT, EMERGENCY_COLD_CHAIN)")
        String priority,

        @Size(max = 300, message = "clinicalNotes must be at most 300 characters")
        String clinicalNotes,

        String idempotencyKey,

        @NotEmpty(message = "At least one order line is required")
        @Valid
        List<OrderLineRequest> items) {

    public record OrderLineRequest(
            @NotBlank(message = "sku is required")
            String sku,

            @Min(value = 1, message = "quantity must be at least 1")
            int quantity) {
    }
}
