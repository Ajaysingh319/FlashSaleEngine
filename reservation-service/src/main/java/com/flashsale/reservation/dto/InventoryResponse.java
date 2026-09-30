package com.flashsale.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class InventoryResponse {
    private String id;
    private String eventId;
    private String ticketTypeId;
    private Integer totalQuantity;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private Integer soldQuantity;
    private Instant updatedAt;
}
