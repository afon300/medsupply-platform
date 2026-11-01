package com.medsupply.warehouse.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {
    List<InventoryBatch> findBySupplyItemSkuOrderByExpiryDateAsc(String sku);

    List<InventoryBatch> findBySupplyItemSkuAndAvailableUnitsGreaterThanOrderByExpiryDateAsc(String sku, int minUnits);
}
