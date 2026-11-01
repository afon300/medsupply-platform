package com.medsupply.warehouse.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {
    Optional<StockReservation> findByIdempotencyKey(String idempotencyKey);

    Optional<StockReservation> findByReservationId(String reservationId);
}
