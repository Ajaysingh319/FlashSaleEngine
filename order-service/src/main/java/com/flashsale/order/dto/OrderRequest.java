package com.flashsale.order.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating an order.
 */
public class OrderRequest {

    @NotBlank
    private String reservationId;

    // Getters and setters
    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

}
