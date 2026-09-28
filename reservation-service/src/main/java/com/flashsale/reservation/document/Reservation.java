package com.flashsale.reservation.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "reservations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    @Id
    private String id;
    private String eventId;
    private String ticketTypeId;
    private String userId;
    private Integer quantity;
    private String status; // PENDING, CONFIRMED, CANCELLED, EXPIRED
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
}