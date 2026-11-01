package com.medsupply.warehouse.exception;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(String reservationId) {
        super("Stock reservation not found in warehouse ledger: " + reservationId);
    }
}
