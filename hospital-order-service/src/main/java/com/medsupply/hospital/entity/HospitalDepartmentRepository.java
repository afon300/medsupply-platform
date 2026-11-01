package com.medsupply.hospital.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface HospitalDepartmentRepository extends JpaRepository<HospitalDepartment, Long> {
    Optional<HospitalDepartment> findByDepartmentCode(String departmentCode);
}
