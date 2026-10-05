package com.flashsale.order.config;

import com.flashsale.order.exception.ReservationServiceUnavailableException;
import com.flashsale.order.kafka.InvalidEventException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class KafkaErrorHandlingConfigTest {
    private static final String MESSAGE = "{\"eventId\":\"evt-1\"}";

    private final KafkaOperations<String, String> kafka = mock(KafkaOperations.class);
    private final Consumer<?, ?> consumer = mock(Consumer.class);
    private final MessageListenerContainer container = mock(MessageListenerContainer.class);
    private final DefaultErrorHandler handler;

    KafkaErrorHandlingConfigTest() {
        OrderKafkaProperties properties = new OrderKafkaProperties();
        properties.getRetry().setBackoffInterval(0);
        handler = new KafkaErrorHandlingConfig().kafkaErrorHandler(kafka, properties);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void invalidEventIsDeadLetteredWithoutRetries() {
        handler.handleRemaining(failure(new InvalidEventException("bad event", null)), records(), consumer, container);

        ProducerRecord<String, String> deadLetter = deadLetter();
        assertEquals("payment.completed.DLT", deadLetter.topic());
        assertEquals(MESSAGE, deadLetter.value());
        assertNotNull(deadLetter.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE), "failure kept for inspection");
    }

    @Test
    void transientFailureIsRetriedThreeTimesThenDeadLettered() {
        Exception failure = failure(new ReservationServiceUnavailableException("down"));

        for (int retry = 1; retry <= 3; retry++) {
            assertThrows(KafkaException.class, () -> handler.handleRemaining(failure, records(), consumer, container));
        }
        verify(kafka, never()).send(any(ProducerRecord.class));

        handler.handleRemaining(failure, records(), consumer, container);
        assertEquals("payment.completed.DLT", deadLetter().topic());
    }

    private static Exception failure(RuntimeException cause) {
        return new ListenerExecutionFailedException("listener failed", cause);
    }

    private static List<ConsumerRecord<?, ?>> records() {
        return new ArrayList<>(List.of(new ConsumerRecord<>("payment.completed", 1, 42L, "order-1", MESSAGE)));
    }

    private ProducerRecord<String, String> deadLetter() {
        ArgumentCaptor<ProducerRecord> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(sent.capture());
        return sent.getValue();
    }
}
