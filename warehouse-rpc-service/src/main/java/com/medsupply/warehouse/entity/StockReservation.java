package com.medsupply.warehouse.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stock_reservations", indexes = {
    @Index(name = "idx_res_id", columnList = "reservationId", unique = true),
    @Index(name = "idx_res_idempotency", columnList = "idempotencyKey", unique = true)
})
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 48)
    private String reservationId;

    @Column(nullable = false, unique = true, length = 96)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String hospitalId;

    @Column(nullable = false, length = 48)
    private String departmentCode;

    @Column(nullable = false, length = 64)
    private String externalOrderRef;

    @Column(nullable = false)
    private boolean priorityEmergency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReservationStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalReservedCostEur;

    @Column(nullable = false)
    private Instant reservedAt;

    @Column
    private Instant releasedAt;

    @Column(length = 200)
    private String releaseReason;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<StockReservationLine> lines = new ArrayList<>();

    protected StockReservation() {
    }

    public StockReservation(
            String reservationId,
            String idempotencyKey,
            String hospitalId,
            String departmentCode,
            String externalOrderRef,
            boolean priorityEmergency,
            BigDecimal totalReservedCostEur,
            Instant reservedAt) {
        this.reservationId = reservationId;
        this.idempotencyKey = idempotencyKey;
        this.hospitalId = hospitalId;
        this.departmentCode = departmentCode;
        this.externalOrderRef = externalOrderRef;
        this.priorityEmergency = priorityEmergency;
        this.status = ReservationStatus.CONFIRMED;
        this.totalReservedCostEur = totalReservedCostEur;
        this.reservedAt = reservedAt;
    }

    public void addLine(StockReservationLine line) {
        this.lines.add(line);
        line.setReservation(this);
    }

    public void markReleased(String reason, Instant timestamp) {
        this.status = ReservationStatus.RELEASED;
        this.releaseReason = reason;
        this.releasedAt = timestamp;
    }

    public void setTotalReservedCostEur(BigDecimal totalReservedCostEur) {
        this.totalReservedCostEur = totalReservedCostEur;
    }

    public Long getId() {
        return id;
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getHospitalId() {
        return hospitalId;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public String getExternalOrderRef() {
        return externalOrderRef;
    }

    public boolean isPriorityEmergency() {
        return priorityEmergency;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalReservedCostEur() {
        return totalReservedCostEur;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public List<StockReservationLine> getLines() {
        return lines;
    }
}
