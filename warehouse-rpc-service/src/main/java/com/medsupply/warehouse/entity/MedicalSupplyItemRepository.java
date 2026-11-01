package com.medsupply.warehouse.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MedicalSupplyItemRepository extends JpaRepository<MedicalSupplyItem, Long> {
    Optional<MedicalSupplyItem> findBySku(String sku);
}
