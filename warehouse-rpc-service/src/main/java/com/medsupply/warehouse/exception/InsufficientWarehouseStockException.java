package com.medsupply.warehouse.exception;

public class InsufficientWarehouseStockException extends RuntimeException {
    private final String sku;
    private final int requested;
    private final int available;

    public InsufficientWarehouseStockException(String sku, int requested, int available) {
        super(String.format(
                "Insufficient warehouse stock for SKU %s: requested %d units, only %d units available across active batches",
                sku, requested, available));
        this.sku = sku;
        this.requested = requested;
        this.available = available;
    }

    public String getSku() {
        return sku;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }
}
