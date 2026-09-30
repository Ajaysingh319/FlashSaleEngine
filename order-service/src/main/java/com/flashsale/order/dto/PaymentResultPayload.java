package com.flashsale.order.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Payment result snapshot consumed by Order; it deliberately has no Payment Service dependency. */
public record PaymentResultPayload(
        String paymentId,
        String orderId,
        String userId,
        BigDecimal amount,
        String status,
        Instant timestamp) { }
