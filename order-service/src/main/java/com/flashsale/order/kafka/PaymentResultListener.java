package com.flashsale.order.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.order.dto.PaymentResultEnvelope;
import com.flashsale.order.dto.PaymentResultPayload;
import com.flashsale.order.exception.InvalidOrderStateException;
import com.flashsale.order.exception.OrderNotFoundException;
import com.flashsale.order.service.OrderPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Thin Kafka adapter for Payment results; all state changes are delegated to OrderPaymentService.
 * Invalid events are dead-lettered at once; stale results for already-settled orders are skipped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentResultListener {
    /** A declined charge (FAILED) and a provider timeout (TIMEOUT) both mean the order was not paid (PRD 6.8). */
    private static final Set<String> FAILURE_STATUSES = Set.of("FAILED", "TIMEOUT");

    private final ObjectMapper objectMapper;
    private final OrderPaymentService orderPaymentService;

    @KafkaListener(topics = {"${order.kafka.topics.payment-completed}", "${order.kafka.topics.payment-result-failed}"},
            groupId = "${order.kafka.payment-result-consumer-group}")
    public void onPaymentResult(String message) {
        PaymentResultEnvelope event = parse(message);
        try {
            orderPaymentService.processPaymentResult(event);
        } catch (IllegalArgumentException mismatch) {
            throw new InvalidEventException("Payment result does not match its order: " + mismatch.getMessage(), mismatch);
        } catch (InvalidOrderStateException | OrderNotFoundException stale) {
            log.warn("Ignoring stale payment result event {}: {}", event.eventId(), stale.getMessage());
        }
        // Any other failure (e.g. Reservation Service unavailable) propagates and is retried, then dead-lettered.
    }

    private PaymentResultEnvelope parse(String message) {
        try {
            PaymentResultEnvelope event = objectMapper.readValue(message, PaymentResultEnvelope.class);
            validate(event);
            return event;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvalidEventException("Invalid payment result event: " + exception.getMessage(), exception);
        }
    }

    private void validate(PaymentResultEnvelope event) {
        if (event == null || blank(event.eventId()) || blank(event.eventType()) || blank(event.aggregateId())
                || event.payload() == null) {
            throw new IllegalArgumentException("eventId, eventType, aggregateId and payload are required");
        }
        PaymentResultPayload payload = event.payload();
        if (blank(payload.paymentId()) || blank(payload.orderId()) || blank(payload.userId())
                || blank(payload.status()) || payload.amount() == null || !event.aggregateId().equals(payload.orderId())) {
            throw new IllegalArgumentException("Payment result payload is incomplete or refers to a different order");
        }
        if ("payment.completed".equals(event.eventType()) && !"SUCCESS".equals(payload.status())) {
            throw new IllegalArgumentException("Completed event must carry SUCCESS status");
        }
        if ("payment.failed".equals(event.eventType()) && !FAILURE_STATUSES.contains(payload.status())) {
            throw new IllegalArgumentException("Failed event must carry FAILED or TIMEOUT status");
        }
        if (!"payment.completed".equals(event.eventType()) && !"payment.failed".equals(event.eventType())) {
            throw new IllegalArgumentException("Unsupported payment result event type");
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
