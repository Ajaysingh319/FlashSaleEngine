package com.flashsale.order.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Processed event record to ensure idempotent processing of Kafka events.
 */
@Document(collection = "processed_events")
public class ProcessedEvent {

    @Id
    private String id;

    @Field("event_id")
    @Indexed(unique = true)
    private String eventId; // UUID of the event

    @Field("event_type")
    private String eventType; // e.g., payment.succeeded

    @Field("processed_at")
    private Instant processedAt;

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
