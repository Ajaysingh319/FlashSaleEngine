package com.flashsale.reservation.controller;

import com.flashsale.reservation.dto.InventoryResponse;
import com.flashsale.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public ticket availability (PRD 3.1 "Display ticket availability", PRD 16 Inventory API). Reservation Service is
 * the inventory authority; these counts are informational and every reservation re-checks them atomically.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PublicInventoryController {
    private final ReservationService reservationService;

    @GetMapping("/events/{eventId}/inventory")
    public ResponseEntity<List<InventoryResponse>> getEventInventory(@PathVariable("eventId") String eventId) {
        return ResponseEntity.ok(reservationService.getInventoryByEventId(eventId));
    }

    @GetMapping("/ticket-types/{ticketTypeId}/inventory")
    public ResponseEntity<InventoryResponse> getTicketTypeInventory(@PathVariable("ticketTypeId") String ticketTypeId) {
        return ResponseEntity.ok(reservationService.getInventory(ticketTypeId));
    }
}
