package com.flashsale.payment.dto;

import com.flashsale.payment.document.Payment;

import java.math.BigDecimal;
import java.time.Instant;

/** GET /api/v1/payments/{paymentId} (PRD 16, TDD 18). */
public record PaymentResponse(String paymentId, String orderId, BigDecimal amount, String paymentMethod, String status,
                              String provider, String transactionId, String failureReason,
                              Instant createdAt, Instant updatedAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getPaymentId(), payment.getOrderId(), payment.getAmount(),
                payment.getPaymentMethod(), payment.getStatus().name(), payment.getProvider(),
                payment.getTransactionId(), payment.getFailureReason(), payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
