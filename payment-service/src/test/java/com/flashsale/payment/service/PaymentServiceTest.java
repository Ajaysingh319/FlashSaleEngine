package com.flashsale.payment.service;

import com.flashsale.payment.document.Payment;
import com.flashsale.payment.document.PaymentProcessedEvent;
import com.flashsale.payment.document.PaymentStatus;
import com.flashsale.payment.dto.PaymentRequestEnvelope;
import com.flashsale.payment.dto.PaymentRequestPayload;
import com.flashsale.payment.observability.PaymentMetrics;
import com.flashsale.payment.outbox.PaymentOutboxService;
import com.flashsale.payment.provider.MockPaymentProvider;
import com.flashsale.payment.provider.PaymentOutcome;
import com.flashsale.payment.repository.PaymentProcessedEventRepository;
import com.flashsale.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentProcessedEventRepository processedEventRepository = mock(PaymentProcessedEventRepository.class);
    private final PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final PaymentService service = new PaymentService(paymentRepository, processedEventRepository,
            new MockPaymentProvider(), outboxService, new PaymentMetrics(registry));

    private static PaymentRequestEnvelope request(String eventId) {
        return request(eventId, "MOCK_CARD");
    }

    private static PaymentRequestEnvelope request(String eventId, String paymentMethod) {
        return new PaymentRequestEnvelope(eventId, "payment.requested", Instant.now(), "ORDER", "order-1",
                new PaymentRequestPayload("order-1", "user-1", new BigDecimal("9998.00"), "pay-1", paymentMethod));
    }

    private Payment processed(String paymentMethod) {
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.empty());
        service.processRequest(request("evt-1", paymentMethod));
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        verify(outboxService).appendResult(saved.getValue());
        return saved.getValue();
    }

    @Test
    void successfulChargeRecordsProviderAndTransactionId() {
        Payment payment = processed("MOCK_CARD");

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals("MOCK", payment.getProvider());
        assertTrue(payment.getTransactionId().startsWith("txn_"));
        assertNull(payment.getFailureReason());
        assertEquals(1, registry.get("payment.success.count").counter().count());
    }

    @Test
    void declinedChargeIsFailedWithReasonAndNoTransaction() {
        Payment payment = processed("MOCK_CARD_DECLINED");

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("Card declined", payment.getFailureReason());
        assertNull(payment.getTransactionId());
        assertEquals(1, registry.get("payment.failure.count").tag("status", "FAILED").counter().count());
    }

    @Test
    void timedOutChargeIsTimeoutWithNoTransaction() {
        Payment payment = processed("MOCK_CARD_TIMEOUT");

        assertEquals(PaymentStatus.TIMEOUT, payment.getStatus());
        assertNull(payment.getTransactionId());
        assertNotNull(payment.getFailureReason());
        assertEquals(1, registry.get("payment.failure.count").tag("status", "TIMEOUT").counter().count());
    }

    @Test
    void processesThePaymentOrderRequestedAndPublishesTheResult() {
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.empty());

        service.processRequest(request("evt-1"));

        ArgumentCaptor<Payment> inserted = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).insert(inserted.capture());
        Payment payment = inserted.getValue();
        assertEquals("pay-1", payment.getPaymentId());
        assertEquals("MOCK_CARD", payment.getPaymentMethod());
        assertEquals("user-1", payment.getUserId());
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        verify(outboxService).appendResult(payment);
        verify(processedEventRepository).insert(any(PaymentProcessedEvent.class));
    }

    @Test
    void duplicateRequestEventIsIgnored() {
        when(processedEventRepository.existsByEventId("evt-1")).thenReturn(true);

        service.processRequest(request("evt-1"));

        verifyNoInteractions(paymentRepository, outboxService);
    }

    // --- Refunds (Order's payment.refund_requested) ---

    private static Payment settled(PaymentOutcome outcome) {
        Payment payment = Payment.start("pay-1", "order-1", "user-1", new BigDecimal("9998.00"), "MOCK_CARD", Instant.now());
        payment.complete("MOCK", outcome, Instant.now());
        return payment;
    }

    @Test
    void successfulPaymentIsRefundedOnce() {
        Payment payment = settled(PaymentOutcome.success("txn_1"));
        when(paymentRepository.findByPaymentId("pay-1")).thenReturn(Optional.of(payment));

        service.processRefund(request("evt-r1"));

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals("txn_1", payment.getTransactionId());
        verify(paymentRepository).save(payment);
        verify(outboxService).appendRefunded(payment);
        verify(processedEventRepository).insert(any(PaymentProcessedEvent.class));

        when(processedEventRepository.existsByEventId("evt-r1")).thenReturn(true);
        service.processRefund(request("evt-r1"));
        verify(paymentRepository, times(1)).save(payment);
        verify(outboxService, times(1)).appendRefunded(payment);
    }

    @Test
    void paymentsThatTookNoMoneyAreNeverRefunded() {
        for (PaymentOutcome outcome : new PaymentOutcome[]{PaymentOutcome.declined("Card declined"), PaymentOutcome.timedOut()}) {
            reset(paymentRepository);
            Payment payment = settled(outcome);
            when(paymentRepository.findByPaymentId("pay-1")).thenReturn(Optional.of(payment));

            service.processRefund(request("evt-r-" + outcome.status()));

            assertEquals(outcome.status(), payment.getStatus());
            verify(paymentRepository, never()).save(any());
            verify(outboxService, never()).appendRefunded(any());
        }
    }

    @Test
    void refundForUnknownPaymentIsRecordedWithoutChanges() {
        when(paymentRepository.findByPaymentId("pay-1")).thenReturn(Optional.empty());

        service.processRefund(request("evt-r2"));

        verify(paymentRepository, never()).save(any());
        verify(processedEventRepository).insert(any(PaymentProcessedEvent.class));
    }

    @Test
    void refundedPaymentCannotBeRefundedAgain() {
        Payment payment = settled(PaymentOutcome.success("txn_1"));
        payment.markRefunded(Instant.now());

        assertFalse(payment.isRefundable());
        assertThrows(IllegalStateException.class, () -> payment.markRefunded(Instant.now()));
    }

    @Test
    void secondRequestForTheSameOrderNeverChargesAgain() {
        when(paymentRepository.findByOrderId("order-1"))
                .thenReturn(Optional.of(Payment.start("pay-1", "order-1", "user-1", BigDecimal.ONE, "MOCK_CARD", Instant.now())));

        service.processRequest(request("evt-2"));

        verify(paymentRepository, never()).insert(any(Payment.class));
        verifyNoInteractions(outboxService);
        verify(processedEventRepository).insert(any(PaymentProcessedEvent.class));
    }
}
