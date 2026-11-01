package com.medsupply.hospital.web;

import com.medsupply.hospital.dto.CreateOrderRequest;
import com.medsupply.hospital.dto.OrderResponseDto;
import com.medsupply.hospital.dto.PlatformMetricsDto;
import com.medsupply.hospital.service.ProcurementOrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ProcurementOrderRestController {

    private static final Logger logger = LoggerFactory.getLogger(ProcurementOrderRestController.class);

    private final ProcurementOrderService orderService;

    @Autowired
    public ProcurementOrderRestController(ProcurementOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/orders")
    public ResponseEntity<List<OrderResponseDto>> listOrders(
            @RequestParam(value = "departmentCode", required = false, defaultValue = "") String departmentCode,
            @RequestParam(value = "status", required = false, defaultValue = "") String status) {
        logger.info("REST GET /api/v1/orders?departmentCode='{}'&status='{}'", departmentCode, status);
        return ResponseEntity.ok(orderService.listOrders(departmentCode, status));
    }

    @GetMapping("/orders/{orderReference}")
    public ResponseEntity<OrderResponseDto> getOrder(@PathVariable("orderReference") String orderReference) {
        logger.info("REST GET /api/v1/orders/{}", orderReference);
        return ResponseEntity.ok(orderService.getOrderByReference(orderReference));
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponseDto> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader) {
        logger.info("REST POST /api/v1/orders for department={} clinician='{}' lines={}",
                request.departmentCode(), request.requestedByClinician(), request.items().size());
        OrderResponseDto created = orderService.createOrder(request, idempotencyKeyHeader);
        HttpStatus status = created.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(created);
    }

    @PostMapping("/orders/{orderReference}/cancel")
    public ResponseEntity<OrderResponseDto> cancelOrder(
            @PathVariable("orderReference") String orderReference,
            @RequestParam(value = "reason", required = false, defaultValue = "Clinical cancellation") String reason) {
        logger.info("REST POST /api/v1/orders/{}/cancel (reason='{}')", orderReference, reason);
        return ResponseEntity.ok(orderService.cancelOrder(orderReference, reason));
    }

    @GetMapping("/metrics/summary")
    public ResponseEntity<PlatformMetricsDto> getMetricsSummary() {
        logger.info("REST GET /api/v1/metrics/summary");
        return ResponseEntity.ok(orderService.getPlatformMetrics());
    }
}
