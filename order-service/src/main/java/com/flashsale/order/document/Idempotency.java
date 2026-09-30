package com.flashsale.order.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.Instant;

/**
 * Idempotency record to prevent duplicate order creation.
 */
@Document(collection = "idempotency")
@CompoundIndex(name = "user_idempotency_key_unique", def = "{'user_id': 1, 'idempotency_key': 1}", unique = true)
public class Idempotency {

    @Id
    private String id;

    @Field("user_id")
    private String userId;

    @Field("idempotency_key")
    private String idempotencyKey;

    @Field("request_fingerprint")
    private String requestFingerprint;

    @Field("order_id")
    private String orderId;

    @Field("created_at")
    private Instant createdAt;

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
