package com.flashsale.reservation.controller;

import com.flashsale.reservation.dto.ReservationResponse;
import com.flashsale.reservation.dto.ReservationLifecycleRequest;
import com.flashsale.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

/** Lookup for authenticated service-to-service callers. */
@RestController
@RequestMapping("/internal/v1/reservations")
@RequiredArgsConstructor
public class InternalReservationController {
    private final ReservationService reservationService;

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getReservation(@PathVariable("id") String id) {
        return ResponseEntity.ok(reservationService.getInternalReservationById(id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ReservationResponse> confirmReservation(@PathVariable("id") String id,
            @Valid @RequestBody ReservationLifecycleRequest request) {
        return ResponseEntity.ok(reservationService.confirmReservationForOrder(id, request.getOrderId(), request.getUserId()));
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<ReservationResponse> releaseReservation(@PathVariable("id") String id,
            @Valid @RequestBody ReservationLifecycleRequest request) {
        return ResponseEntity.ok(reservationService.releaseReservationForOrder(id, request.getOrderId(), request.getUserId()));
    }
}
