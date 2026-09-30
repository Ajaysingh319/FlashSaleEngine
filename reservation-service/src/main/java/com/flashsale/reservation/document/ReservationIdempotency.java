package com.flashsale.reservation.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "reservation_idempotency")
@CompoundIndex(name = "user_idempotency_key_unique", def = "{'userId': 1, 'idempotencyKey': 1}", unique = true)
@Data
public class ReservationIdempotency {
    @Id
    private String id;
    private String userId;
    private String idempotencyKey;
    private String requestFingerprint;
    private String reservationId;
    private Instant createdAt;
}
