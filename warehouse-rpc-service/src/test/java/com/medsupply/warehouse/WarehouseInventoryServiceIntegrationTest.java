package com.medsupply.warehouse;

import com.medsupply.warehouse.exception.InsufficientWarehouseStockException;
import com.medsupply.warehouse.service.WarehouseInventoryService;
import com.medsupply.warehouse.service.model.CatalogSummaryView;
import com.medsupply.warehouse.service.model.ReservationOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:warehouse_test_db;DB_CLOSE_DELAY=-1",
    "grpc.server.port=-1",
    "grpc.server.inProcessName=warehouse-test-server"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WarehouseInventoryServiceIntegrationTest {

    @Autowired
    private WarehouseInventoryService inventoryService;

    @Test
    void shouldSeedCatalogAndAllocateAcrossMultipleBatchesInFefoOrder() {
        List<CatalogSummaryView> catalog = inventoryService.listCatalog("", "");
        assertThat(catalog).hasSize(6);

        // MED-SUR-003 has Batch 1 (8 units, exp 2027-05-10) and Batch 2 (16 units, exp 2028-03-25) -> 24 total
        CatalogSummaryView before = inventoryService.getCatalogSummaryBySku("MED-SUR-003");
        assertThat(before.totalAvailableUnits()).isEqualTo(24);

        // Request 10 units -> must exhaust 8 units from earliest batch LOT-26-STER-04 and 2 units from LOT-28-STER-19
        ReservationOutcome outcome = inventoryService.reserveStock(
                "IDEMP-SUR-10",
                "HOSP-SAINT-LOUIS",
                "DEPT-SURG",
                "ORD-TEST-001",
                true,
                Map.of("MED-SUR-003", 10));

        assertThat(outcome.replayedFromIdempotency()).isFalse();
        assertThat(outcome.reservation().getLines()).hasSize(2);
        assertThat(outcome.reservation().getLines().get(0).getBatchLotNumber()).isEqualTo("LOT-26-STER-04");
        assertThat(outcome.reservation().getLines().get(0).getAllocatedQuantity()).isEqualTo(8);
        assertThat(outcome.reservation().getLines().get(1).getBatchLotNumber()).isEqualTo("LOT-28-STER-19");
        assertThat(outcome.reservation().getLines().get(1).getAllocatedQuantity()).isEqualTo(2);

        CatalogSummaryView afterReserve = inventoryService.getCatalogSummaryBySku("MED-SUR-003");
        assertThat(afterReserve.totalAvailableUnits()).isEqualTo(14);

        // Replaying exact same idempotency key must NOT deduct another 10 units
        ReservationOutcome replay = inventoryService.reserveStock(
                "IDEMP-SUR-10",
                "HOSP-SAINT-LOUIS",
                "DEPT-SURG",
                "ORD-TEST-001",
                true,
                Map.of("MED-SUR-003", 10));
        assertThat(replay.replayedFromIdempotency()).isTrue();
        assertThat(replay.reservation().getReservationId()).isEqualTo(outcome.reservation().getReservationId());
        assertThat(inventoryService.getCatalogSummaryBySku("MED-SUR-003").totalAvailableUnits()).isEqualTo(14);

        // Compensating release restores all 10 units back to the batches
        int restored = inventoryService.releaseReservation(outcome.reservation().getReservationId(), "Test rollback");
        assertThat(restored).isEqualTo(10);
        assertThat(inventoryService.getCatalogSummaryBySku("MED-SUR-003").totalAvailableUnits()).isEqualTo(24);
    }

    @Test
    void shouldRejectExcessiveReservationWithoutPartialStockMutation() {
        assertThatThrownBy(() -> inventoryService.reserveStock(
                "IDEMP-OVERFLOW",
                "HOSP-SAINT-LOUIS",
                "DEPT-ONCO",
                "ORD-TEST-999",
                false,
                Map.of("MED-ONC-006", 500)))
                .isInstanceOf(InsufficientWarehouseStockException.class)
                .hasMessageContaining("MED-ONC-006");

        // Total stock of MED-ONC-006 (9 + 15 = 24) remains untouched due to @Transactional rollback
        assertThat(inventoryService.getCatalogSummaryBySku("MED-ONC-006").totalAvailableUnits()).isEqualTo(24);
    }
}
