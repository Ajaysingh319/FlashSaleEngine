package com.flashsale.payment.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flashsale.payment.config.PaymentKafkaProperties;
import com.flashsale.payment.document.Payment;
import com.flashsale.payment.document.PaymentOutboxEvent;
import com.flashsale.payment.provider.PaymentOutcome;
import com.flashsale.payment.repository.PaymentOutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentOutboxServiceTest {

    private final PaymentOutboxEventRepository repository = mock(PaymentOutboxEventRepository.class);
    private final PaymentOutboxService outboxService;

    PaymentOutboxServiceTest() {
        PaymentKafkaProperties properties = new PaymentKafkaProperties();
        properties.getTopics().setCompleted("payment.completed");
        properties.getTopics().setFailed("payment.failed");
        properties.getTopics().setRefunded("payment.refunded");
        outboxService = new PaymentOutboxService(repository, properties, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    private PaymentOutboxEvent appendedFor(PaymentOutcome outcome) {
        Payment payment = Payment.start("pay-1", "order-1", "user-1", new BigDecimal("9998.00"), "MOCK_CARD", Instant.now());
        payment.complete("MOCK", outcome, Instant.now());
        outboxService.appendResult(payment);
        ArgumentCaptor<PaymentOutboxEvent> event = ArgumentCaptor.forClass(PaymentOutboxEvent.class);
        verify(repository).insert(event.capture());
        return event.getValue();
    }

    @Test
    void successIsPublishedAsCompleted() {
        PaymentOutboxEvent event = appendedFor(PaymentOutcome.success("txn_1"));

        assertEquals("payment.completed", event.getTopic());
        assertTrue(event.getPayload().contains("\"status\":\"SUCCESS\""));
    }

    @Test
    void declineAndTimeoutArePublishedAsFailedWithTheirStatus() {
        assertTrue(appendedFor(PaymentOutcome.declined("Card declined")).getPayload().contains("\"status\":\"FAILED\""));
        reset(repository);
        PaymentOutboxEvent timeout = appendedFor(PaymentOutcome.timedOut());

        assertEquals("payment.failed", timeout.getTopic());
        assertTrue(timeout.getPayload().contains("\"status\":\"TIMEOUT\""));
    }

    @Test
    void refundIsConfirmedOnTheRefundedTopic() {
        Payment payment = Payment.start("pay-1", "order-1", "user-1", new BigDecimal("9998.00"), "MOCK_CARD", Instant.now());
        payment.complete("MOCK", PaymentOutcome.success("txn_1"), Instant.now());
        payment.markRefunded(Instant.now());

        outboxService.appendRefunded(payment);

        ArgumentCaptor<PaymentOutboxEvent> event = ArgumentCaptor.forClass(PaymentOutboxEvent.class);
        verify(repository).insert(event.capture());
        assertEquals("payment.refunded", event.getValue().getTopic());
        assertEquals("payment.refunded", event.getValue().getEventType());
        assertEquals("order-1", event.getValue().getAggregateId());
        assertTrue(event.getValue().getPayload().contains("\"status\":\"REFUNDED\""));
    }
}
