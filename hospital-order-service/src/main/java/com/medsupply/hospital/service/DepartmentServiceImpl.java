package com.medsupply.hospital.service;

import com.medsupply.hospital.dto.DepartmentDto;
import com.medsupply.hospital.entity.HospitalDepartment;
import com.medsupply.hospital.entity.HospitalDepartmentRepository;
import com.medsupply.hospital.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class DepartmentServiceImpl implements DepartmentService {

    private static final Logger logger = LoggerFactory.getLogger(DepartmentServiceImpl.class);

    private final HospitalDepartmentRepository departmentRepository;

    @Autowired
    public DepartmentServiceImpl(HospitalDepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentDto> listAllDepartments() {
        List<DepartmentDto> list = departmentRepository.findAll().stream()
                .sorted(Comparator.comparing(HospitalDepartment::getDepartmentCode))
                .map(this::toDto)
                .toList();
        logger.info("Retrieved {} hospital clinical departments", list.size());
        return list;
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentDto getDepartmentByCode(String departmentCode) {
        HospitalDepartment department = departmentRepository.findByDepartmentCode(departmentCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Hospital department not found: " + departmentCode));
        return toDto(department);
    }

    private DepartmentDto toDto(HospitalDepartment dept) {
        return new DepartmentDto(
                dept.getDepartmentCode(),
                dept.getName(),
                dept.getHeadPhysician(),
                dept.getQuarterlyBudgetEur(),
                dept.getCommittedSpendEur(),
                dept.getRemainingBudgetEur());
    }
}
