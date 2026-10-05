package com.flashsale.reservation.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "reservations")
@CompoundIndex(name = "status_expiry_idx", def = "{'status': 1, 'expiresAt': 1}")
@CompoundIndex(name = "user_event_status_idx", def = "{'userId': 1, 'eventId': 1, 'status': 1}")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    @Id
    private String id;
    @Indexed
    private String eventId;
    private String ticketTypeId;
    @Indexed
    private String userId;
    private String orderId;
    private Integer quantity;
    private BigDecimal unitPrice; // ticket-type price from Catalog at reservation time (TDD 16)
    private BigDecimal amount;    // unitPrice x quantity
    private String status; // ACTIVE, CONFIRMED, CANCELLED, EXPIRED
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
}
