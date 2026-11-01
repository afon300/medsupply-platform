package com.medsupply.warehouse.exception;

public class SupplyItemNotFoundException extends RuntimeException {
    public SupplyItemNotFoundException(String sku) {
        super("Medical supply SKU not found in central warehouse catalog: " + sku);
    }
}
