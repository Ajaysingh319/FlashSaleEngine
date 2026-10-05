package com.flashsale.payment.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.payment.config.PaymentKafkaProperties;
import com.flashsale.payment.document.Payment;
import com.flashsale.payment.document.PaymentStatus;
import com.flashsale.payment.document.PaymentOutboxEvent;
import com.flashsale.payment.dto.PaymentResultEnvelope;
import com.flashsale.payment.dto.PaymentResultPayload;
import com.flashsale.payment.repository.PaymentOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** Writes result events into the current Payment Mongo transaction. */
@Service
@RequiredArgsConstructor
public class PaymentOutboxService {
    private final PaymentOutboxEventRepository outboxRepository;
    private final PaymentKafkaProperties kafkaProperties;
    private final ObjectMapper objectMapper;

    public void appendResult(Payment payment) {
        Instant timestamp = Instant.now();
        String eventId = UUID.randomUUID().toString();
        String topic = payment.getStatus() == PaymentStatus.SUCCESS
                ? kafkaProperties.getTopics().getCompleted() : kafkaProperties.getTopics().getFailed();
        String eventType = payment.getStatus() == PaymentStatus.SUCCESS ? "payment.completed" : "payment.failed";
        PaymentOutboxEvent event = new PaymentOutboxEvent();
        event.setEventId(eventId);
        event.setAggregateId(payment.getOrderId());
        event.setTopic(topic);
        event.setEventType(eventType);
        event.setPayload(serialize(eventId, eventType, timestamp, payment));
        event.setCreatedAt(timestamp);
        event.setPublishAttempts(0);
        outboxRepository.insert(event);
    }

    private String serialize(String eventId, String eventType, Instant timestamp, Payment payment) {
        PaymentResultPayload payload = new PaymentResultPayload(payment.getPaymentId(), payment.getOrderId(),
                payment.getUserId(), payment.getAmount(), payment.getStatus().name(), timestamp);
        try {
            return objectMapper.writeValueAsString(new PaymentResultEnvelope(eventId, eventType, timestamp,
                    "PAYMENT", payment.getOrderId(), payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize payment result event", exception);
        }
    }
}
