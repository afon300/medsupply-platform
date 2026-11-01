package com.medsupply.hospital.service;

import com.medsupply.hospital.dto.CreateOrderRequest;
import com.medsupply.hospital.dto.OrderResponseDto;
import com.medsupply.hospital.dto.PlatformMetricsDto;

import java.util.List;

public interface ProcurementOrderService {

    OrderResponseDto createOrder(CreateOrderRequest request, String headerIdempotencyKey);

    List<OrderResponseDto> listOrders(String departmentCode, String statusFilter);

    OrderResponseDto getOrderByReference(String orderReference);

    OrderResponseDto cancelOrder(String orderReference, String reason);

    PlatformMetricsDto getPlatformMetrics();
}
