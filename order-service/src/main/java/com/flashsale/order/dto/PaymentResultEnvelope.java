package com.flashsale.order.dto;

import java.time.Instant;

/** Local wire contract for Payment-originated Kafka result events. */
public record PaymentResultEnvelope(
        String eventId,
        String eventType,
        Instant timestamp,
        String aggregateType,
        String aggregateId,
        PaymentResultPayload payload) { }
