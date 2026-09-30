package com.flashsale.reservation.controller;

import com.flashsale.reservation.dto.InventoryInitializationRequest;
import com.flashsale.reservation.dto.InventoryResponse;
import com.flashsale.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal setup API until the catalog-to-reservation inventory provisioning contract is implemented. */
@RestController
@RequestMapping("/internal/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<InventoryResponse> initialize(@Valid @RequestBody InventoryInitializationRequest request) {
        return ResponseEntity.ok(reservationService.initializeInventory(request));
    }

    @GetMapping("/{ticketTypeId}")
    public ResponseEntity<InventoryResponse> get(@PathVariable String ticketTypeId) {
        return ResponseEntity.ok(reservationService.getInventory(ticketTypeId));
    }
}
