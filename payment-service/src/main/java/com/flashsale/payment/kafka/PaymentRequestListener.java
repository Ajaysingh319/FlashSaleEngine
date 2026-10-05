package com.flashsale.payment.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.payment.dto.PaymentRequestEnvelope;
import com.flashsale.payment.dto.PaymentRequestPayload;
import com.flashsale.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka adapter for Order-originated payment events (charge and refund requests). Parsing and validation stay
 * here; all processing is delegated to PaymentService. Malformed events are dead-lettered at once; processing
 * failures propagate so they are retried and then dead-lettered (KafkaErrorHandlingConfig).
 */
@Component
@RequiredArgsConstructor
public class PaymentRequestListener {
    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;

    @KafkaListener(topics = "${payment.kafka.topics.requested}", groupId = "${payment.kafka.consumer-group}")
    public void onPaymentRequested(String message) {
        paymentService.processRequest(parse(message));
    }

    @KafkaListener(topics = "${payment.kafka.topics.refund-requested}", groupId = "${payment.kafka.consumer-group}")
    public void onRefundRequested(String message) {
        paymentService.processRefund(parse(message));
    }

    private PaymentRequestEnvelope parse(String message) {
        try {
            PaymentRequestEnvelope event = objectMapper.readValue(message, PaymentRequestEnvelope.class);
            validate(event);
            return event;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvalidEventException("Invalid payment event: " + exception.getMessage(), exception);
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
