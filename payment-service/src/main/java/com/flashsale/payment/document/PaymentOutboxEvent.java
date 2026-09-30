package com.flashsale.payment.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "outbox_events")
@CompoundIndex(name = "outbox_unpublished_created_idx", def = "{'publishedAt': 1, 'createdAt': 1}")
public class PaymentOutboxEvent {
    @Id private String id;
    @Indexed(unique = true) private String eventId;
    private String aggregateId;
    private String topic;
    private String eventType;
    private String payload;
    private Instant createdAt;
    private Instant publishedAt;
    private int publishAttempts;
    private String lastPublishError;
    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getEventId() { return eventId; } public void setEventId(String eventId) { this.eventId = eventId; }
    public String getAggregateId() { return aggregateId; } public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public String getTopic() { return topic; } public void setTopic(String topic) { this.topic = topic; }
    public String getEventType() { return eventType; } public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; } public void setPayload(String payload) { this.payload = payload; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getPublishedAt() { return publishedAt; } public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public int getPublishAttempts() { return publishAttempts; } public void setPublishAttempts(int publishAttempts) { this.publishAttempts = publishAttempts; }
    public String getLastPublishError() { return lastPublishError; } public void setLastPublishError(String lastPublishError) { this.lastPublishError = lastPublishError; }
}
