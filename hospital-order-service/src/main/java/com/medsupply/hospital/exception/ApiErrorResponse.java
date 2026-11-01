package com.medsupply.hospital.exception;

import java.util.Map;

public record ApiErrorResponse(
        String timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String correlationId,
        Map<String, String> fieldErrors) {
}
