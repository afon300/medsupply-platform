package com.medsupply.warehouse.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "stock_reservation_lines")
public class StockReservationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private StockReservation reservation;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private InventoryBatch batch;

    @Column(nullable = false, length = 32)
    private String sku;

    @Column(nullable = false, length = 160)
    private String itemName;

    @Column(nullable = false, length = 48)
    private String batchLotNumber;

    @Column(nullable = false)
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StorageCondition storageCondition;

    @Column(nullable = false)
    private int allocatedQuantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceEur;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotalEur;

    protected StockReservationLine() {
    }

    public StockReservationLine(
            InventoryBatch batch,
            String sku,
            String itemName,
            String batchLotNumber,
            LocalDate expiryDate,
            StorageCondition storageCondition,
            int allocatedQuantity,
            BigDecimal unitPriceEur,
            BigDecimal lineTotalEur) {
        this.batch = batch;
        this.sku = sku;
        this.itemName = itemName;
        this.batchLotNumber = batchLotNumber;
        this.expiryDate = expiryDate;
        this.storageCondition = storageCondition;
        this.allocatedQuantity = allocatedQuantity;
        this.unitPriceEur = unitPriceEur;
        this.lineTotalEur = lineTotalEur;
    }

    void setReservation(StockReservation reservation) {
        this.reservation = reservation;
    }

    public InventoryBatch getBatch() {
        return batch;
    }

    public String getSku() {
        return sku;
    }

    public String getItemName() {
        return itemName;
    }

    public String getBatchLotNumber() {
        return batchLotNumber;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public StorageCondition getStorageCondition() {
        return storageCondition;
    }

    public int getAllocatedQuantity() {
        return allocatedQuantity;
    }

    public BigDecimal getUnitPriceEur() {
        return unitPriceEur;
    }

    public BigDecimal getLineTotalEur() {
        return lineTotalEur;
    }
}
