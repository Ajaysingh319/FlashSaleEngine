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

/** Writes payment events into the current Payment Mongo transaction. */
@Service
@RequiredArgsConstructor
public class PaymentOutboxService {
    static final String PAYMENT_COMPLETED = "payment.completed";
    static final String PAYMENT_FAILED = "payment.failed";
    static final String PAYMENT_REFUNDED = "payment.refunded";

    private final PaymentOutboxEventRepository outboxRepository;
    private final PaymentKafkaProperties kafkaProperties;
    private final ObjectMapper objectMapper;

    /** The charge outcome: payment.completed for SUCCESS, payment.failed for FAILED or TIMEOUT. */
    public void appendResult(Payment payment) {
        PaymentKafkaProperties.Topics topics = kafkaProperties.getTopics();
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            append(payment, topics.getCompleted(), PAYMENT_COMPLETED);
        } else {
            append(payment, topics.getFailed(), PAYMENT_FAILED);
        }
    }

    /** Confirms to Order that the money of an unfulfillable order was returned. */
    public void appendRefunded(Payment payment) {
        append(payment, kafkaProperties.getTopics().getRefunded(), PAYMENT_REFUNDED);
    }

    private void append(Payment payment, String topic, String eventType) {
        Instant timestamp = Instant.now();
        String eventId = UUID.randomUUID().toString();
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
            throw new IllegalStateException("Could not serialize payment event", exception);
        }
    }
}
