package com.medsupply.warehouse.service.model;

import com.medsupply.warehouse.entity.StockReservation;

public record ReservationOutcome(
        StockReservation reservation,
        boolean replayedFromIdempotency) {
}
