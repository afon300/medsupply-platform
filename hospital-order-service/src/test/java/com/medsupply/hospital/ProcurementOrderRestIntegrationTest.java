package com.medsupply.hospital;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medsupply.hospital.dto.CatalogItemDto;
import com.medsupply.hospital.dto.CreateOrderRequest;
import com.medsupply.hospital.exception.UpstreamRpcUnavailableException;
import com.medsupply.hospital.exception.WarehouseStockConflictException;
import com.medsupply.hospital.service.WarehouseRpcGateway;
import com.medsupply.rpc.warehouse.BatchAllocation;
import com.medsupply.rpc.warehouse.ReleaseReservationResponse;
import com.medsupply.rpc.warehouse.ReserveStockResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:hospital_test_db;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProcurementOrderRestIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WarehouseRpcGateway warehouseGateway;

    @Test
    void shouldCreateStatelessOrderSupportIdempotentReplayAndCancelWithCompensation() throws Exception {
        ReserveStockResponse mockedRpcResponse = ReserveStockResponse.newBuilder()
                .setReservationId("RSV-TEST9001")
                .setStatus("CONFIRMED")
                .setTotalReservedCostEur(490.00)
                .setReplayedFromIdempotency(false)
                .setReservedAtIso(Instant.now().toString())
                .addAllocations(BatchAllocation.newBuilder()
                        .setSku("MED-VAC-001")
                        .setItemName("mRNA Respiratory Booster Vial (10-Dose)")
                        .setBatchLotNumber("LOT-26-CRYO-01")
                        .setExpiryDate("2027-02-15")
                        .setStorageCondition("ULTRA_COLD_MINUS_70C")
                        .setAllocatedQuantity(2)
                        .setUnitPriceEur(245.00)
                        .setLineTotalEur(490.00)
                        .build())
                .build();

        when(warehouseGateway.reserveWarehouseStock(anyString(), anyString(), eq("DEPT-ICU"), anyString(), eq(true), anyMap()))
                .thenReturn(mockedRpcResponse);
        when(warehouseGateway.releaseWarehouseStock(eq("RSV-TEST9001"), anyString()))
                .thenReturn(ReleaseReservationResponse.newBuilder()
                        .setReservationId("RSV-TEST9001")
                        .setStatus("RELEASED")
                        .setRestoredTotalUnits(2)
                        .setReleasedAtIso(Instant.now().toString())
                        .build());

        CreateOrderRequest payload = new CreateOrderRequest(
                "DEPT-ICU",
                "Dr. Marc Laurent",
                "EMERGENCY_COLD_CHAIN",
                "ICU Winter Respiratory Booster Resupply",
                null,
                List.of(new CreateOrderRequest.OrderLineRequest("MED-VAC-001", 2)));

        String responseJson = mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "KEY-ICU-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.warehouseReservationId").value("RSV-TEST9001"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.allocatedBatches[0].batchLotNumber").value("LOT-26-CRYO-01"))
                .andReturn().getResponse().getContentAsString();

        String orderRef = objectMapper.readTree(responseJson).get("orderReference").asText();

        // Idempotent replay with same header returns 200 OK without calling warehouse RPC a second time
        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "KEY-ICU-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderReference").value(orderRef))
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        verify(warehouseGateway, times(1)).reserveWarehouseStock(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyMap());

        // Cancel order triggers compensating ReleaseReservation RPC and refunds department budget
        mockMvc.perform(post("/api/v1/orders/" + orderRef + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(warehouseGateway, times(1)).releaseWarehouseStock(eq("RSV-TEST9001"), anyString());
    }

    @Test
    void shouldExecuteSagaCompensationAndReturn422WhenDepartmentBudgetExceeded() throws Exception {
        // DEPT-EMERG has 28,000 budget - 24,100 committed = 3,900.00 EUR remaining
        ReserveStockResponse expensiveReservation = ReserveStockResponse.newBuilder()
                .setReservationId("RSV-OVERBUDGET")
                .setStatus("CONFIRMED")
                .setTotalReservedCostEur(5000.00)
                .setReservedAtIso(Instant.now().toString())
                .build();

        when(warehouseGateway.reserveWarehouseStock(anyString(), anyString(), eq("DEPT-EMERG"), anyString(), anyBoolean(), anyMap()))
                .thenReturn(expensiveReservation);

        CreateOrderRequest payload = new CreateOrderRequest(
                "DEPT-EMERG",
                "Dr. Julien Garnier",
                "URGENT",
                "Emergency trauma stent kits",
                "KEY-OVERBUDGET",
                List.of(new CreateOrderRequest.OrderLineRequest("MED-SUR-003", 4)));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_BUDGET_EXCEEDED"))
                .andExpect(jsonPath("$.status").value(422));

        // Verify Saga compensation released the warehouse reservation automatically!
        verify(warehouseGateway, times(1)).releaseWarehouseStock(eq("RSV-OVERBUDGET"), contains("InsufficientBudgetException"));
    }

    @Test
    void shouldReturnStructuredHttpErrorsFor400And404And409And503() throws Exception {
        // 400 Validation Error
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentCode\":\"\",\"requestedByClinician\":\"\",\"priority\":\"STANDARD\",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // 404 Not Found
        mockMvc.perform(get("/api/v1/orders/ORD-NONEXISTENT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 409 Warehouse Stock Conflict
        when(warehouseGateway.reserveWarehouseStock(anyString(), anyString(), eq("DEPT-SURG"), anyString(), anyBoolean(), anyMap()))
                .thenThrow(new WarehouseStockConflictException("Insufficient warehouse stock for SKU MED-SUR-003"));

        CreateOrderRequest conflictReq = new CreateOrderRequest(
                "DEPT-SURG", "Dr. Antoine Lefevre", "STANDARD", "Test conflict", "KEY-409",
                List.of(new CreateOrderRequest.OrderLineRequest("MED-SUR-003", 999)));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAREHOUSE_STOCK_CONFLICT"));

        // 503 Upstream RPC Unavailable
        when(warehouseGateway.fetchCatalog(anyString(), anyString()))
                .thenThrow(new UpstreamRpcUnavailableException("Central warehouse gRPC service is currently unreachable", new RuntimeException()));

        mockMvc.perform(get("/api/v1/catalog"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UPSTREAM_RPC_UNAVAILABLE"))
                .andExpect(jsonPath("$.status").value(503));
    }
}
