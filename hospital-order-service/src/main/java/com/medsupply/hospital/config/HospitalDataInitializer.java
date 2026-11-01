package com.medsupply.hospital.config;

import com.medsupply.hospital.entity.HospitalDepartment;
import com.medsupply.hospital.entity.HospitalDepartmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
public class HospitalDataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(HospitalDataInitializer.class);

    private final HospitalDepartmentRepository departmentRepository;

    @Autowired
    public HospitalDataInitializer(HospitalDepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (departmentRepository.count() > 0) {
            logger.info("Hospital DBMS already initialized ({} departments present)", departmentRepository.count());
            return;
        }

        logger.info("Seeding Hospital Clinical Departments into dedicated Hospital DBMS...");

        departmentRepository.save(new HospitalDepartment(
                "DEPT-ICU",
                "Intensive Care & Resuscitation",
                "Prof. Claire Moreau, MD",
                new BigDecimal("45000.00"),
                new BigDecimal("8200.00")));

        departmentRepository.save(new HospitalDepartment(
                "DEPT-SURG",
                "Cardiovascular & Vascular Surgery",
                "Dr. Antoine Lefevre, MD",
                new BigDecimal("62000.00"),
                new BigDecimal("14500.00")));

        departmentRepository.save(new HospitalDepartment(
                "DEPT-ONCO",
                "Clinical Oncology & Immunotherapy",
                "Prof. Nadia Benali, MD",
                new BigDecimal("54000.00"),
                new BigDecimal("19800.00")));

        departmentRepository.save(new HospitalDepartment(
                "DEPT-EMERG",
                "Emergency & Acute Triage Unit",
                "Dr. Julien Garnier, MD",
                new BigDecimal("28000.00"),
                new BigDecimal("24100.00")));

        logger.info("Hospital DBMS seeded with {} clinical departments", departmentRepository.count());
    }
}
