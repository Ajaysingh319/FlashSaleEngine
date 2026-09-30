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

/** Kafka adapter only: parsing and validation stay separate from payment processing. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRequestListener {
    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;

    @KafkaListener(topics = "${payment.kafka.topics.requested}", groupId = "${payment.kafka.consumer-group}")
    public void onPaymentRequested(String message) {
        PaymentRequestEnvelope event;
        try {
            event = objectMapper.readValue(message, PaymentRequestEnvelope.class);
            validate(event);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            log.warn("Ignoring malformed payment request event: {}", exception.getMessage());
            return;
        }
        paymentService.processRequest(event);
    }

    private void validate(PaymentRequestEnvelope event) {
        if (event == null || blank(event.eventId()) || event.payload() == null) {
            throw new IllegalArgumentException("eventId and payload are required");
        }
        PaymentRequestPayload payload = event.payload();
        if (blank(payload.orderId()) || blank(payload.userId()) || payload.amount() == null) {
            throw new IllegalArgumentException("payload orderId, userId and amount are required");
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
