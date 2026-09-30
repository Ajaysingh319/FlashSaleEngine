package com.flashsale.payment.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "payments")
public class Payment {
    @Id private String id;
    @Indexed(unique = true) private String paymentId;
    @Indexed(unique = true) private String orderId;
    private String userId;
    private BigDecimal amount;
    private PaymentStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public void initialize(String paymentId, String orderId, String userId, BigDecimal amount, Instant now) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void complete(PaymentStatus result, Instant now) {
        if (status != PaymentStatus.PENDING || (result != PaymentStatus.SUCCESS && result != PaymentStatus.FAILED)) {
            throw new IllegalStateException("Invalid payment status transition");
        }
        status = result;
        updatedAt = now;
    }

    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getPaymentId() { return paymentId; } public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public String getOrderId() { return orderId; } public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getUserId() { return userId; } public void setUserId(String userId) { this.userId = userId; }
    public BigDecimal getAmount() { return amount; } public void setAmount(BigDecimal amount) { this.amount = amount; }
    public PaymentStatus getStatus() { return status; } public void setStatus(PaymentStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
