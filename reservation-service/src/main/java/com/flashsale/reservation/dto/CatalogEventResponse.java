package com.flashsale.reservation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Subset of Catalog Service's EventResponse (GET /api/v1/events/{eventId}) needed for sale eligibility. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CatalogEventResponse {
    private String id;
    private String status;
    private Instant saleStartTime;
    private Instant saleEndTime;
}
