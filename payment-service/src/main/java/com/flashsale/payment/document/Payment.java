package com.flashsale.payment.document;

import com.flashsale.payment.provider.PaymentOutcome;
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
    private String paymentMethod;
    private PaymentStatus status;
    private String provider;
    /** Provider reference of a successful charge; unique when present (TDD 18). */
    @Indexed(unique = true, sparse = true) private String transactionId;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    /** A new payment for an order, PENDING until the provider answers. The paymentId is assigned by Order. */
    public static Payment start(String paymentId, String orderId, String userId, BigDecimal amount,
                                String paymentMethod, Instant now) {
        Payment payment = new Payment();
        payment.paymentId = paymentId;
        payment.orderId = orderId;
        payment.userId = userId;
        payment.amount = amount;
        payment.paymentMethod = paymentMethod;
        payment.status = PaymentStatus.PENDING;
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    /** Records the provider's answer; a payment is settled exactly once. */
    public void complete(String providerName, PaymentOutcome outcome, Instant now) {
        if (status != PaymentStatus.PENDING || outcome.status() == PaymentStatus.PENDING) {
            throw new IllegalStateException("Invalid payment status transition");
        }
        provider = providerName;
        status = outcome.status();
        transactionId = outcome.transactionId();
        failureReason = outcome.failureReason();
        updatedAt = now;
    }

    /** Only money actually taken can be returned. */
    public boolean isRefundable() { return status == PaymentStatus.SUCCESS; }

    public void markRefunded(Instant now) {
        if (!isRefundable()) {
            throw new IllegalStateException("Only a successful payment can be refunded");
        }
        status = PaymentStatus.REFUNDED;
        updatedAt = now;
    }

    public boolean isOwnedBy(String candidateUserId) { return userId != null && userId.equals(candidateUserId); }

    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getPaymentId() { return paymentId; } public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public String getOrderId() { return orderId; } public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getUserId() { return userId; } public void setUserId(String userId) { this.userId = userId; }
    public BigDecimal getAmount() { return amount; } public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getPaymentMethod() { return paymentMethod; } public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public PaymentStatus getStatus() { return status; } public void setStatus(PaymentStatus status) { this.status = status; }
    public String getProvider() { return provider; } public void setProvider(String provider) { this.provider = provider; }
    public String getTransactionId() { return transactionId; } public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getFailureReason() { return failureReason; } public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
