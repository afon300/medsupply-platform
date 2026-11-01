package com.medsupply.warehouse.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "medical_supply_items", indexes = {
    @Index(name = "idx_supply_sku", columnList = "sku", unique = true),
    @Index(name = "idx_supply_category", columnList = "category")
})
public class MedicalSupplyItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String sku;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SupplyCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StorageCondition storageCondition;

    @Column(nullable = false, length = 48)
    private String unitOfMeasure;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceEur;

    @OneToMany(mappedBy = "supplyItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryBatch> batches = new ArrayList<>();

    protected MedicalSupplyItem() {
    }

    public MedicalSupplyItem(
            String sku,
            String name,
            SupplyCategory category,
            StorageCondition storageCondition,
            String unitOfMeasure,
            BigDecimal unitPriceEur) {
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.storageCondition = storageCondition;
        this.unitOfMeasure = unitOfMeasure;
        this.unitPriceEur = unitPriceEur;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public SupplyCategory getCategory() {
        return category;
    }

    public StorageCondition getStorageCondition() {
        return storageCondition;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public BigDecimal getUnitPriceEur() {
        return unitPriceEur;
    }

    public List<InventoryBatch> getBatches() {
        return batches;
    }
}
