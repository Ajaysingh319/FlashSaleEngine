package com.flashsale.reservation.dto;

import com.flashsale.reservation.document.Reservation;
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

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(reservation.getId(), reservation.getEventId(), reservation.getTicketTypeId(),
                reservation.getUserId(), reservation.getQuantity(), reservation.getStatus(), reservation.getCreatedAt(),
                reservation.getUpdatedAt(), reservation.getExpiresAt(), reservation.getUnitPrice(), reservation.getAmount());
    }
}
