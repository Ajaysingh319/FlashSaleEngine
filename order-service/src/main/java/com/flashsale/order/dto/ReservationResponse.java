package com.flashsale.order.dto;

import java.time.Instant;

/** Wire contract returned by Reservation Service's authenticated internal endpoint. */
public class ReservationResponse {
    private String reservationId;
    private String userId;
    private String eventId;
    private String ticketTypeId;
    private Integer quantity;
    private String status;
    private Instant expiresAt;

    public String getReservationId() { return reservationId; } public void setReservationId(String reservationId) { this.reservationId = reservationId; }
    public String getUserId() { return userId; } public void setUserId(String userId) { this.userId = userId; }
    public String getEventId() { return eventId; } public void setEventId(String eventId) { this.eventId = eventId; }
    public String getTicketTypeId() { return ticketTypeId; } public void setTicketTypeId(String ticketTypeId) { this.ticketTypeId = ticketTypeId; }
    public Integer getQuantity() { return quantity; } public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
    public Instant getExpiresAt() { return expiresAt; } public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
