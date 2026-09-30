package com.flashsale.reservation.outbox;

import com.flashsale.reservation.document.ReservationOutboxEvent;
import com.flashsale.reservation.repository.ReservationOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Publishes committed outbox records. Consumers must still tolerate at-least-once delivery. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationOutboxPublisher {
    private final ReservationOutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${reservation.outbox.publish.interval:5000}")
    public void publishPending() {
        for (ReservationOutboxEvent event : outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload()).get();
                event.setPublishedAt(Instant.now());
                event.setPublishAttempts(event.getPublishAttempts() + 1);
                event.setLastPublishError(null);
                outboxRepository.save(event);
            } catch (Exception exception) {
                event.setPublishAttempts(event.getPublishAttempts() + 1);
                event.setLastPublishError(exception.getClass().getSimpleName());
                outboxRepository.save(event);
                log.warn("Could not publish reservation outbox event {}", event.getEventId(), exception);
            }
        }
    }
}
