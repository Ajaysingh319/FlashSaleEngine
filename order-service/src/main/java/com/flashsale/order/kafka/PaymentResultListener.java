package com.flashsale.order.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.order.dto.PaymentResultEnvelope;
import com.flashsale.order.dto.PaymentResultPayload;
import com.flashsale.order.exception.InvalidOrderStateException;
import com.flashsale.order.exception.OrderNotFoundException;
import com.flashsale.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Thin Kafka adapter for Payment results; all state changes are delegated to OrderService. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentResultListener {
    private final ObjectMapper objectMapper;
    private final OrderService orderService;

    @KafkaListener(topics = {"${order.kafka.topics.payment-completed}", "${order.kafka.topics.payment-result-failed}"},
            groupId = "${order.kafka.payment-result-consumer-group}")
    public void onPaymentResult(String message) {
        PaymentResultEnvelope event;
        try {
            event = objectMapper.readValue(message, PaymentResultEnvelope.class);
            validate(event);
            orderService.processPaymentResult(event);
        } catch (JsonProcessingException | IllegalArgumentException | InvalidOrderStateException | OrderNotFoundException exception) {
            log.warn("Ignoring invalid or stale payment result event: {}", exception.getMessage());
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
        if ("payment.failed".equals(event.eventType()) && !"FAILED".equals(payload.status())) {
            throw new IllegalArgumentException("Failed event must carry FAILED status");
        }
        if (!"payment.completed".equals(event.eventType()) && !"payment.failed".equals(event.eventType())) {
            throw new IllegalArgumentException("Unsupported payment result event type");
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
