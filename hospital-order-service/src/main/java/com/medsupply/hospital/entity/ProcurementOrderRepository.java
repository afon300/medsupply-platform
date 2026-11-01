package com.medsupply.hospital.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProcurementOrderRepository extends JpaRepository<ProcurementOrder, Long> {
    Optional<ProcurementOrder> findByOrderReference(String orderReference);

    Optional<ProcurementOrder> findByIdempotencyKey(String idempotencyKey);

    List<ProcurementOrder> findAllByOrderByCreatedAtDesc();

    List<ProcurementOrder> findByDepartmentDepartmentCodeOrderByCreatedAtDesc(String departmentCode);
}
