package com.medsupply.warehouse.config;

import com.medsupply.warehouse.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class WarehouseDataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(WarehouseDataInitializer.class);

    private final MedicalSupplyItemRepository supplyItemRepository;
    private final InventoryBatchRepository batchRepository;

    @Autowired
    public WarehouseDataInitializer(
            MedicalSupplyItemRepository supplyItemRepository,
            InventoryBatchRepository batchRepository) {
        this.supplyItemRepository = supplyItemRepository;
        this.batchRepository = batchRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (supplyItemRepository.count() > 0) {
            logger.info("Warehouse DBMS already initialized ({} SKUs present)", supplyItemRepository.count());
            return;
        }

        logger.info("Seeding Central Regional Medical Warehouse DBMS with clinical SKUs and multi-expiry FEFO batches...");

        seedItem("MED-VAC-001", "mRNA Respiratory Booster Vial (10-Dose)",
                SupplyCategory.VACCINE, StorageCondition.ULTRA_COLD_MINUS_70C, "10-Dose Vial", "245.00",
                "LOT-26-CRYO-01", 18, LocalDate.of(2027, 2, 15), "CRYO-VAULT-A1",
                "LOT-27-CRYO-09", 42, LocalDate.of(2027, 8, 30), "CRYO-VAULT-A2");

        seedItem("MED-BIO-002", "Monoclonal Immunoglobulin IV Infusion 50mL",
                SupplyCategory.BIOLOGIC, StorageCondition.REFRIGERATED_2_TO_8C, "50mL Flacon", "680.00",
                "LOT-26-COLD-11", 12, LocalDate.of(2027, 1, 20), "COLD-ROOM-B1",
                "LOT-27-COLD-24", 25, LocalDate.of(2027, 7, 14), "COLD-ROOM-B2");

        seedItem("MED-SUR-003", "Titanium Endovascular Stent Graft Kit",
                SupplyCategory.SURGICAL, StorageCondition.CONTROLLED_ROOM_15_TO_25C, "Sterile Kit", "1250.00",
                "LOT-26-STER-04", 8, LocalDate.of(2027, 5, 10), "STERILE-BAY-C1",
                "LOT-28-STER-19", 16, LocalDate.of(2028, 3, 25), "STERILE-BAY-C2");

        seedItem("MED-CAR-004", "Heparin Sodium Anticoagulant Syringe Pack",
                SupplyCategory.CARDIOLOGY, StorageCondition.CONTROLLED_ROOM_15_TO_25C, "Box of 20 Syringes", "165.00",
                "LOT-26-AMB-31", 35, LocalDate.of(2027, 3, 5), "AMBIENT-RACK-D1",
                "LOT-27-AMB-52", 60, LocalDate.of(2027, 11, 18), "AMBIENT-RACK-D3");

        seedItem("MED-ANE-005", "Propofol Emulsion 1% Injectable Tray",
                SupplyCategory.ANESTHESIA, StorageCondition.REFRIGERATED_2_TO_8C, "Tray of 10 Ampoules", "195.00",
                "LOT-26-COLD-44", 20, LocalDate.of(2027, 2, 28), "COLD-ROOM-B3",
                "LOT-27-COLD-67", 40, LocalDate.of(2027, 9, 12), "COLD-ROOM-B3");

        seedItem("MED-ONC-006", "Targeted Cisplatin Chemotherapy Concentrate",
                SupplyCategory.ONCOLOGY, StorageCondition.REFRIGERATED_2_TO_8C, "100mg Sterile Vial", "890.00",
                "LOT-26-ONCO-07", 9, LocalDate.of(2027, 1, 31), "CYTO-VAULT-E1",
                "LOT-27-ONCO-15", 15, LocalDate.of(2027, 6, 30), "CYTO-VAULT-E2");

        logger.info("Warehouse DBMS seeded successfully: {} SKUs, {} batch lots",
                supplyItemRepository.count(), batchRepository.count());
    }

    private void seedItem(
            String sku, String name, SupplyCategory category, StorageCondition condition,
            String uom, String unitPrice,
            String lot1, int qty1, LocalDate exp1, String zone1,
            String lot2, int qty2, LocalDate exp2, String zone2) {

        MedicalSupplyItem item = supplyItemRepository.save(
                new MedicalSupplyItem(sku, name, category, condition, uom, new BigDecimal(unitPrice)));

        batchRepository.save(new InventoryBatch(item, lot1, qty1, exp1, zone1));
        batchRepository.save(new InventoryBatch(item, lot2, qty2, exp2, zone2));
    }
}
