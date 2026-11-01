package com.medsupply.hospital.dto;

import java.math.BigDecimal;

public record DepartmentDto(
        String departmentCode,
        String name,
        String headPhysician,
        BigDecimal quarterlyBudgetEur,
        BigDecimal committedSpendEur,
        BigDecimal remainingBudgetEur) {
}
