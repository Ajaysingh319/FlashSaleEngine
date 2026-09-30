package com.flashsale.reservation.controller;

import com.flashsale.reservation.dto.ReservationResponse;
import com.flashsale.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lookup for authenticated service-to-service callers. */
@RestController
@RequestMapping("/internal/v1/reservations")
@RequiredArgsConstructor
public class InternalReservationController {
    private final ReservationService reservationService;

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getReservation(@PathVariable String id) {
        return ResponseEntity.ok(reservationService.getInternalReservationById(id));
    }
}
