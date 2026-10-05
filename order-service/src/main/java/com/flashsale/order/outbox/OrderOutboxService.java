package com.flashsale.order.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.order.config.OrderKafkaProperties;
import com.flashsale.order.document.Order;
import com.flashsale.order.document.OrderOutboxEvent;
import com.flashsale.order.dto.OrderEventEnvelope;
import com.flashsale.order.dto.OrderLifecycleEventPayload;
import com.flashsale.order.repository.OrderOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** Appends Order lifecycle events; callers include this write in their Mongo transaction. */
@Service
@RequiredArgsConstructor
public class OrderOutboxService {
    private static final String AGGREGATE_TYPE = "ORDER";

    private final OrderOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final OrderKafkaProperties kafkaProperties;

    public void appendCreated(Order order) { append(order, "order.created", kafkaProperties.getTopics().getCreated()); }
    public void appendCancelled(Order order) { append(order, "order.cancelled", kafkaProperties.getTopics().getCancelled()); }
    public void appendConfirmed(Order order) { append(order, "order.confirmed", kafkaProperties.getTopics().getConfirmed()); }
    public void appendPaymentFailed(Order order) { append(order, "order.payment_failed", kafkaProperties.getTopics().getPaymentFailed()); }
    public void appendPaymentRequested(Order order) { append(order, "payment.requested", kafkaProperties.getTopics().getPaymentRequested()); }
    public void appendRefundRequested(Order order) { append(order, "payment.refund_requested", kafkaProperties.getTopics().getPaymentRefundRequested()); }

    private void append(Order order, String eventType, String topic) {
        Instant timestamp = Instant.now();
        String eventId = UUID.randomUUID().toString();
        OrderOutboxEvent event = new OrderOutboxEvent();
        event.setEventId(eventId);
        event.setAggregateId(order.getOrderId());
        event.setTopic(topic);
        event.setEventType(eventType);
        event.setPayload(serialize(eventId, eventType, timestamp, order));
        event.setCreatedAt(timestamp);
        event.setPublishAttempts(0);
        outboxRepository.insert(event);
    }

    private String serialize(String eventId, String eventType, Instant timestamp, Order order) {
        OrderLifecycleEventPayload payload = new OrderLifecycleEventPayload(order.getOrderId(), order.getUserId(),
                order.getReservationId(), order.getEventId(), order.getTicketTypeId(), order.getQuantity(),
                order.getUnitPrice(), order.getTotalAmount(), order.getStatus().name(), order.getPaymentStatus().name(),
                order.getPaymentId(), order.getPaymentMethod());
        try {
            return objectMapper.writeValueAsString(new OrderEventEnvelope(eventId, eventType, timestamp,
                    AGGREGATE_TYPE, order.getOrderId(), payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize Order outbox event", exception);
        }
    }
}
