package com.medsupply.warehouse.entity;

public enum StorageCondition {
    ULTRA_COLD_MINUS_70C(true),
    REFRIGERATED_2_TO_8C(true),
    CONTROLLED_ROOM_15_TO_25C(false);

    private final boolean coldChainRequired;

    StorageCondition(boolean coldChainRequired) {
        this.coldChainRequired = coldChainRequired;
    }

    public boolean isColdChainRequired() {
        return coldChainRequired;
    }
}
