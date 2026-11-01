package com.medsupply.hospital.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "procurement_orders", indexes = {
    @Index(name = "idx_order_ref", columnList = "orderReference", unique = true),
    @Index(name = "idx_order_idempotency", columnList = "idempotencyKey", unique = true)
})
public class ProcurementOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 48)
    private String orderReference;

    @Column(nullable = false, unique = true, length = 96)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String hospitalId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private HospitalDepartment department;

    @Column(nullable = false, length = 120)
    private String requestedByClinician;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status;

    @Column(nullable = false, length = 48)
    private String warehouseReservationId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmountEur;

    @Column(length = 300)
    private String clinicalNotes;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant cancelledAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderLineItem> lineItems = new ArrayList<>();

    protected ProcurementOrder() {
    }

    public ProcurementOrder(
            String orderReference,
            String idempotencyKey,
            String hospitalId,
            HospitalDepartment department,
            String requestedByClinician,
            OrderPriority priority,
            String warehouseReservationId,
            BigDecimal totalAmountEur,
            String clinicalNotes,
            Instant createdAt) {
        this.orderReference = orderReference;
        this.idempotencyKey = idempotencyKey;
        this.hospitalId = hospitalId;
        this.department = department;
        this.requestedByClinician = requestedByClinician;
        this.priority = priority;
        this.status = OrderStatus.CONFIRMED;
        this.warehouseReservationId = warehouseReservationId;
        this.totalAmountEur = totalAmountEur;
        this.clinicalNotes = clinicalNotes;
        this.createdAt = createdAt;
    }

    public void addLineItem(OrderLineItem item) {
        this.lineItems.add(item);
        item.setOrder(this);
    }

    public void markCancelled(Instant timestamp) {
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = timestamp;
    }

    public Long getId() {
        return id;
    }

    public String getOrderReference() {
        return orderReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getHospitalId() {
        return hospitalId;
    }

    public HospitalDepartment getDepartment() {
        return department;
    }

    public String getRequestedByClinician() {
        return requestedByClinician;
    }

    public OrderPriority getPriority() {
        return priority;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getWarehouseReservationId() {
        return warehouseReservationId;
    }

    public BigDecimal getTotalAmountEur() {
        return totalAmountEur;
    }

    public String getClinicalNotes() {
        return clinicalNotes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public List<OrderLineItem> getLineItems() {
        return lineItems;
    }
}
