package com.medsupply.hospital.web;

import com.medsupply.hospital.dto.DepartmentDto;
import com.medsupply.hospital.service.DepartmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentRestController {

    private static final Logger logger = LoggerFactory.getLogger(DepartmentRestController.class);

    private final DepartmentService departmentService;

    @Autowired
    public DepartmentRestController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public ResponseEntity<List<DepartmentDto>> listDepartments() {
        logger.info("REST GET /api/v1/departments");
        return ResponseEntity.ok(departmentService.listAllDepartments());
    }

    @GetMapping("/{code}")
    public ResponseEntity<DepartmentDto> getDepartment(@PathVariable("code") String code) {
        logger.info("REST GET /api/v1/departments/{}", code);
        return ResponseEntity.ok(departmentService.getDepartmentByCode(code));
    }
}
