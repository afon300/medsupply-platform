package com.medsupply.warehouse.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "inventory_batches", indexes = {
    @Index(name = "idx_batch_lot", columnList = "batchLotNumber", unique = true),
    @Index(name = "idx_batch_expiry", columnList = "expiryDate")
})
public class InventoryBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supply_item_id", nullable = false)
    private MedicalSupplyItem supplyItem;

    @Column(nullable = false, unique = true, length = 48)
    private String batchLotNumber;

    @Column(nullable = false)
    private int availableUnits;

    @Column(nullable = false)
    private int initialUnits;

    @Column(nullable = false)
    private LocalDate expiryDate;

    @Column(nullable = false, length = 40)
    private String warehouseZone;

    @Version
    private Long version;

    protected InventoryBatch() {
    }

    public InventoryBatch(
            MedicalSupplyItem supplyItem,
            String batchLotNumber,
            int initialUnits,
            LocalDate expiryDate,
            String warehouseZone) {
        this.supplyItem = supplyItem;
        this.batchLotNumber = batchLotNumber;
        this.initialUnits = initialUnits;
        this.availableUnits = initialUnits;
        this.expiryDate = expiryDate;
        this.warehouseZone = warehouseZone;
    }

    public void deductUnits(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Deduction count must be strictly positive");
        }
        if (count > this.availableUnits) {
            throw new IllegalStateException("Cannot deduct " + count + " units from batch " + batchLotNumber);
        }
        this.availableUnits -= count;
    }

    public void restoreUnits(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Restore count must be strictly positive");
        }
        this.availableUnits += count;
    }

    public Long getId() {
        return id;
    }

    public MedicalSupplyItem getSupplyItem() {
        return supplyItem;
    }

    public String getBatchLotNumber() {
        return batchLotNumber;
    }

    public int getAvailableUnits() {
        return availableUnits;
    }

    public int getInitialUnits() {
        return initialUnits;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public String getWarehouseZone() {
        return warehouseZone;
    }
}
