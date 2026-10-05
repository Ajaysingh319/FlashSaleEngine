package com.flashsale.payment.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.payment.dto.PaymentRequestEnvelope;
import com.flashsale.payment.dto.PaymentRequestPayload;
import com.flashsale.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Kafka adapter for Order-originated payment events (charge and refund requests). Parsing and validation stay
 * here; all processing is delegated to PaymentService. Malformed events are logged and skipped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRequestListener {
    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;

    @KafkaListener(topics = "${payment.kafka.topics.requested}", groupId = "${payment.kafka.consumer-group}")
    public void onPaymentRequested(String message) {
        parse(message).ifPresent(paymentService::processRequest);
    }

    @KafkaListener(topics = "${payment.kafka.topics.refund-requested}", groupId = "${payment.kafka.consumer-group}")
    public void onRefundRequested(String message) {
        parse(message).ifPresent(paymentService::processRefund);
    }

    private Optional<PaymentRequestEnvelope> parse(String message) {
        try {
            PaymentRequestEnvelope event = objectMapper.readValue(message, PaymentRequestEnvelope.class);
            validate(event);
            return Optional.of(event);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            log.warn("Ignoring malformed payment event: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private void validate(PaymentRequestEnvelope event) {
        if (event == null || blank(event.eventId()) || event.payload() == null) {
            throw new IllegalArgumentException("eventId and payload are required");
        }
        PaymentRequestPayload payload = event.payload();
        if (blank(payload.orderId()) || blank(payload.userId()) || payload.amount() == null
                || blank(payload.paymentId()) || blank(payload.paymentMethod())) {
            throw new IllegalArgumentException("payload orderId, userId, amount, paymentId and paymentMethod are required");
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
