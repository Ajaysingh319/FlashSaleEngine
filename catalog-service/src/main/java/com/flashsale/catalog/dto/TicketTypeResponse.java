package com.flashsale.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketTypeResponse {

    private String id;
    private String name;
    private Double price;
    private Integer totalQuantity;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private Integer soldQuantity;
    private String eventId;
    private Instant createdAt;
    private Instant updatedAt;
}