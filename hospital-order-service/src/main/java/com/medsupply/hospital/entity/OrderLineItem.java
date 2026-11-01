package com.medsupply.hospital.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "procurement_order_lines")
public class OrderLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private ProcurementOrder order;

    @Column(nullable = false, length = 32)
    private String sku;

    @Column(nullable = false, length = 160)
    private String itemName;

    @Column(nullable = false, length = 48)
    private String batchLotNumber;

    @Column(nullable = false, length = 24)
    private String expiryDate;

    @Column(nullable = false, length = 40)
    private String storageCondition;

    @Column(nullable = false)
    private int allocatedQuantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceEur;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotalEur;

    protected OrderLineItem() {
    }

    public OrderLineItem(
            String sku,
            String itemName,
            String batchLotNumber,
            String expiryDate,
            String storageCondition,
            int allocatedQuantity,
            BigDecimal unitPriceEur,
            BigDecimal lineTotalEur) {
        this.sku = sku;
        this.itemName = itemName;
        this.batchLotNumber = batchLotNumber;
        this.expiryDate = expiryDate;
        this.storageCondition = storageCondition;
        this.allocatedQuantity = allocatedQuantity;
        this.unitPriceEur = unitPriceEur;
        this.lineTotalEur = lineTotalEur;
    }

    void setOrder(ProcurementOrder order) {
        this.order = order;
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

    public String getExpiryDate() {
        return expiryDate;
    }

    public String getStorageCondition() {
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
