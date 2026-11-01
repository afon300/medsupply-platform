package com.medsupply.hospital.exception;

public class UpstreamRpcUnavailableException extends RuntimeException {
    public UpstreamRpcUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
