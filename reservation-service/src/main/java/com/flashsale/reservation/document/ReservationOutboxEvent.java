package com.flashsale.reservation.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "outbox_events")
@CompoundIndex(name = "outbox_pending_idx", def = "{'publishedAt': 1, 'createdAt': 1}")
@Data
public class ReservationOutboxEvent {
    @Id private String id;
    private String eventId;
    private String aggregateType;
    private String aggregateId;
    private String topic;
    private String eventType;
    private String payload;
    private String traceId; // request trace ID (TDD 68), sent as the X-Correlation-ID Kafka header
    private Instant createdAt;
    private Instant publishedAt;
    private Integer publishAttempts;
    private String lastPublishError;
}
