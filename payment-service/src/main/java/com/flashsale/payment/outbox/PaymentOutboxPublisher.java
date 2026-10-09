package com.flashsale.payment.outbox;

import com.flashsale.payment.observability.KafkaTraceHeader;
import com.flashsale.payment.document.PaymentOutboxEvent;
import com.flashsale.payment.repository.PaymentOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentOutboxPublisher {
    private final PaymentOutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${payment.kafka.outbox-publish-interval:5000}")
    public void publishPending() {
        for (PaymentOutboxEvent event : outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()) {
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
                log.warn("Could not publish payment outbox event {}", event.getEventId(), exception);
            }
        }
    }
}
