package com.medsupply.hospital.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalRestExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalRestExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        logger.warn("HTTP 404 Not Found on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(WarehouseStockConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleStockConflict(
            WarehouseStockConflictException ex, HttpServletRequest request) {
        logger.warn("HTTP 409 Stock Conflict on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "WAREHOUSE_STOCK_CONFLICT", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ApiErrorResponse> handleOrderStateConflict(
            InvalidOrderStateException ex, HttpServletRequest request) {
        logger.warn("HTTP 409 Invalid Order State on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "INVALID_ORDER_STATE", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InsufficientBudgetException.class)
    public ResponseEntity<ApiErrorResponse> handleInsufficientBudget(
            InsufficientBudgetException ex, HttpServletRequest request) {
        logger.warn("HTTP 422 Budget Exceeded on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "DEPARTMENT_BUDGET_EXCEEDED", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        String summary = "Request payload validation failed (" + fieldErrors.size() + " constraint violation(s))";
        logger.warn("HTTP 400 Validation Error on {}: {}", request.getRequestURI(), fieldErrors);
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", summary, request, fieldErrors);
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
            Exception ex, HttpServletRequest request) {
        logger.warn("HTTP 400 Bad Request on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(UpstreamRpcUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleUpstreamUnavailable(
            UpstreamRpcUnavailableException ex, HttpServletRequest request) {
        logger.error("HTTP 503 Upstream gRPC Warehouse Unavailable on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_RPC_UNAVAILABLE", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        logger.error("HTTP 500 Unexpected error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected internal error occurred while processing the request",
                request,
                Map.of());
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors) {
        String corrId = MDC.get("correlationId");
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now().toString(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                corrId != null ? corrId : "N/A",
                fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
