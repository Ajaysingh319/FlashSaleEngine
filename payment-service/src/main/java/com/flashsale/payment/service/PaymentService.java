package com.flashsale.payment.service;

import com.flashsale.payment.document.Payment;
import com.flashsale.payment.document.PaymentProcessedEvent;
import com.flashsale.payment.dto.PaymentRequestEnvelope;
import com.flashsale.payment.dto.PaymentRequestPayload;
import com.flashsale.payment.observability.PaymentMetrics;
import com.flashsale.payment.provider.PaymentProvider;
import com.flashsale.payment.outbox.PaymentOutboxService;
import com.flashsale.payment.repository.PaymentProcessedEventRepository;
import com.flashsale.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Owns payment persistence and processing; Kafka listener remains a thin adapter. */
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentProcessedEventRepository processedEventRepository;
    private final PaymentProvider paymentProvider;
    private final PaymentOutboxService paymentOutboxService;
    private final PaymentMetrics paymentMetrics;

    @Transactional
    public void processRequest(PaymentRequestEnvelope event) {
        if (processedEventRepository.existsByEventId(event.eventId())) return;

        PaymentRequestPayload request = event.payload();
        if (paymentRepository.findByOrderId(request.orderId()).isPresent()) {
            recordProcessed(event.eventId());
            return;
        }

        Payment payment = Payment.start(request.paymentId(), request.orderId(), request.userId(), request.amount(),
                request.paymentMethod(), Instant.now());
        paymentRepository.insert(payment);

        payment.complete(paymentProvider.name(), paymentProvider.charge(payment), Instant.now());
        paymentRepository.save(payment);
        paymentOutboxService.appendResult(payment);
        paymentMetrics.chargeCompleted(payment.getStatus());
        recordProcessed(event.eventId());
    }

    /**
     * Refunds a successful payment whose order could not be fulfilled (Order's payment.refund_requested).
     * Exactly once per event; payments that were never charged, or are already refunded, are left unchanged.
     * A completed refund is confirmed to Order with payment.refunded, written in the same transaction.
     */
    @Transactional
    public void processRefund(PaymentRequestEnvelope event) {
        if (processedEventRepository.existsByEventId(event.eventId())) return;

        paymentRepository.findByPaymentId(event.payload().paymentId())
                .filter(Payment::isRefundable)
                .ifPresent(payment -> {
                    paymentProvider.refund(payment);
                    payment.markRefunded(Instant.now());
                    paymentRepository.save(payment);
                    paymentOutboxService.appendRefunded(payment);
                });
        recordProcessed(event.eventId());
    }

    private void recordProcessed(String eventId) {
        PaymentProcessedEvent processed = new PaymentProcessedEvent();
        processed.setEventId(eventId);
        processed.setProcessedAt(Instant.now());
        processedEventRepository.insert(processed);
    }
}
