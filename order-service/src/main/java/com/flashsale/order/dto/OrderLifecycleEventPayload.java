package com.flashsale.order.dto;

import java.math.BigDecimal;

/** Immutable order snapshot carried in an Order lifecycle event. */
public record OrderLifecycleEventPayload(
        String orderId,
        String userId,
        String reservationId,
        String eventId,
        String ticketTypeId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String status,
        String paymentStatus) { }
