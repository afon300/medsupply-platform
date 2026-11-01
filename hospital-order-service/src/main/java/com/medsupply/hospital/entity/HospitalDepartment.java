package com.medsupply.hospital.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "hospital_departments", indexes = {
    @Index(name = "idx_dept_code", columnList = "departmentCode", unique = true)
})
public class HospitalDepartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String departmentCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 120)
    private String headPhysician;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal quarterlyBudgetEur;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal committedSpendEur;

    @Version
    private Long version;

    protected HospitalDepartment() {
    }

    public HospitalDepartment(
            String departmentCode,
            String name,
            String headPhysician,
            BigDecimal quarterlyBudgetEur,
            BigDecimal committedSpendEur) {
        this.departmentCode = departmentCode;
        this.name = name;
        this.headPhysician = headPhysician;
        this.quarterlyBudgetEur = quarterlyBudgetEur;
        this.committedSpendEur = committedSpendEur;
    }

    public BigDecimal getRemainingBudgetEur() {
        return quarterlyBudgetEur.subtract(committedSpendEur);
    }

    public void commitBudget(BigDecimal amount) {
        this.committedSpendEur = this.committedSpendEur.add(amount);
    }

    public void refundBudget(BigDecimal amount) {
        BigDecimal updated = this.committedSpendEur.subtract(amount);
        this.committedSpendEur = updated.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : updated;
    }

    public Long getId() {
        return id;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public String getName() {
        return name;
    }

    public String getHeadPhysician() {
        return headPhysician;
    }

    public BigDecimal getQuarterlyBudgetEur() {
        return quarterlyBudgetEur;
    }

    public BigDecimal getCommittedSpendEur() {
        return committedSpendEur;
    }
}
