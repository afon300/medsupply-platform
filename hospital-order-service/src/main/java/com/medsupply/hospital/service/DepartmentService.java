package com.medsupply.hospital.service;

import com.medsupply.hospital.dto.DepartmentDto;

import java.util.List;

public interface DepartmentService {

    List<DepartmentDto> listAllDepartments();

    DepartmentDto getDepartmentByCode(String departmentCode);
}
