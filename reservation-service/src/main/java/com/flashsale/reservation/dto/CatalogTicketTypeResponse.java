package com.flashsale.reservation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Subset of Catalog Service's TicketTypeResponse (GET /api/v1/ticket-types/{id}) needed to price a reservation. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CatalogTicketTypeResponse {
    private String id;
    private String eventId;
    private Double price;
}
