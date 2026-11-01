package com.medsupply.hospital.exception;

import java.math.BigDecimal;

public class InsufficientBudgetException extends RuntimeException {
    public InsufficientBudgetException(String departmentCode, BigDecimal required, BigDecimal remaining) {
        super(String.format(
                "Department %s lacks sufficient remaining quarterly budget: order requires %.2f EUR, remaining budget is %.2f EUR",
                departmentCode, required, remaining));
    }
}
