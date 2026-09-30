package com.flashsale.order.dto;

import java.time.Instant;

/** Versioned wire shape for all Order-originated Kafka events. */
public record OrderEventEnvelope(
        String eventId,
        String eventType,
        Instant timestamp,
        String aggregateType,
        String aggregateId,
        OrderLifecycleEventPayload payload) { }
