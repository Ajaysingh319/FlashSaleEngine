package com.flashsale.order.outbox;

import com.flashsale.order.observability.KafkaTraceHeader;
import com.flashsale.order.document.OrderOutboxEvent;
import com.flashsale.order.repository.OrderOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Publishes committed outbox records. A crash after Kafka acknowledgement can yield at-least-once delivery. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderOutboxPublisher {
    private final OrderOutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${order.kafka.outbox-publish-interval:5000}")
    public void publishPending() {
        for (OrderOutboxEvent event : outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            publish(event);
        }
    }

    private void publish(OrderOutboxEvent event) {
        try {
            kafkaTemplate.send(KafkaTraceHeader.record(
                    event.getTopic(), event.getAggregateId(), event.getPayload(), event.getTraceId())).get();
            event.setPublishedAt(Instant.now());
            event.setPublishAttempts(event.getPublishAttempts() + 1);
            event.setLastPublishError(null);
            outboxRepository.save(event);
        } catch (Exception exception) {
            event.setPublishAttempts(event.getPublishAttempts() + 1);
            event.setLastPublishError(exception.getClass().getSimpleName());
            outboxRepository.save(event);
            log.warn("Could not publish Order outbox event {}", event.getEventId(), exception);
        }
    }
}
