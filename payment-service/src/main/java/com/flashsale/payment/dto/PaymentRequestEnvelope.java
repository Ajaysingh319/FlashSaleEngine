package com.flashsale.payment.dto;

import java.time.Instant;

/** Local contract for the Order-originated request message. */
public record PaymentRequestEnvelope(
        String eventId,
        String eventType,
        Instant timestamp,
        String aggregateType,
        String aggregateId,
        PaymentRequestPayload payload) { }
