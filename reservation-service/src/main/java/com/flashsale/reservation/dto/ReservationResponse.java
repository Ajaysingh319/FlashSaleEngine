package com.flashsale.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {
    private String reservationId;
    private String eventId;
    private String ticketTypeId;
    private String userId;
    private Integer quantity;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
    private BigDecimal unitPrice;
    private BigDecimal amount;
}
