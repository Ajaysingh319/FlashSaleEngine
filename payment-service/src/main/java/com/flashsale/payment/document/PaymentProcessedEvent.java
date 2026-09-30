package com.flashsale.payment.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Records successfully handled source events to make Kafka delivery idempotent. */
@Document(collection = "processed_events")
public class PaymentProcessedEvent {
    @Id private String id;
    @Indexed(unique = true) private String eventId;
    private Instant processedAt;
    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getEventId() { return eventId; } public void setEventId(String eventId) { this.eventId = eventId; }
    public Instant getProcessedAt() { return processedAt; } public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
