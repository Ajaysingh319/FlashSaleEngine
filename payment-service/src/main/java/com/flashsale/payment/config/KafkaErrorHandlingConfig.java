package com.flashsale.payment.config;

import com.flashsale.payment.kafka.InvalidEventException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Consumer failure policy (TDD 66-67): a failing record is retried a bounded number of times, then parked on
 * {@code <topic>.DLT} with the failure in its headers for later inspection. Invalid events skip the retries.
 * Spring Boot applies this handler to every @KafkaListener container.
 */
@Configuration
public class KafkaErrorHandlingConfig {
    static final String DEAD_LETTER_SUFFIX = ".DLT";

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<String, String> kafkaTemplate,
                                                 PaymentKafkaProperties properties) {
        // Partition -1 lets Kafka choose, so a DLT does not need as many partitions as its source topic.
        DeadLetterPublishingRecoverer deadLetters = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, -1));
        PaymentKafkaProperties.Retry retry = properties.getRetry();
        DefaultErrorHandler handler = new DefaultErrorHandler(deadLetters,
                new FixedBackOff(retry.getBackoffInterval(), retry.getAttempts()));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
