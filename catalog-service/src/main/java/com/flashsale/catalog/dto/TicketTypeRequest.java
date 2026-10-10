package com.flashsale.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Ticket type setup; the event comes from the URL (POST /api/v1/events/{eventId}/ticket-types). totalQuantity is
 * the initial stock: Reservation Service initializes inventory with available = totalQuantity and reserved = sold = 0,
 * and owns those counts from then on.
 */
public class TicketTypeRequest {

    @NotBlank
    private String name;

    @NotNull
    @Positive
    private Double price;

    @NotNull
    @Positive
    private Integer totalQuantity;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Integer totalQuantity) {
        this.totalQuantity = totalQuantity;
    }
}
