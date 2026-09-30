package com.flashsale.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResultPayload(
        String paymentId,
        String orderId,
        String userId,
        BigDecimal amount,
        String status,
        Instant timestamp) { }
