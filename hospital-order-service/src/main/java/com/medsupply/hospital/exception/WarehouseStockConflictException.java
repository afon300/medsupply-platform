package com.medsupply.hospital.exception;

public class WarehouseStockConflictException extends RuntimeException {
    public WarehouseStockConflictException(String message) {
        super(message);
    }
}
