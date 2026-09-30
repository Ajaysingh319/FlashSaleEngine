package com.flashsale.payment.dto;

import java.time.Instant;

/** Stable wire envelope emitted after mock payment completion. */
public record PaymentResultEnvelope(
        String eventId,
        String eventType,
        Instant timestamp,
        String aggregateType,
        String aggregateId,
        PaymentResultPayload payload) { }
