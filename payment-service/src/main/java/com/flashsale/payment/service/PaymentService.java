package com.flashsale.payment.service;

import com.flashsale.payment.document.Payment;
import com.flashsale.payment.document.PaymentProcessedEvent;
import com.flashsale.payment.document.PaymentStatus;
import com.flashsale.payment.dto.PaymentRequestEnvelope;
import com.flashsale.payment.dto.PaymentRequestPayload;
import com.flashsale.payment.outbox.PaymentOutboxService;
import com.flashsale.payment.repository.PaymentProcessedEventRepository;
import com.flashsale.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Owns payment persistence and processing; Kafka listener remains a thin adapter. */
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentProcessedEventRepository processedEventRepository;
    private final MockPaymentProcessor paymentProcessor;
    private final PaymentOutboxService paymentOutboxService;

    @Transactional
    public void processRequest(PaymentRequestEnvelope event) {
        if (processedEventRepository.existsByEventId(event.eventId())) return;

        PaymentRequestPayload request = event.payload();
        if (paymentRepository.findByOrderId(request.orderId()).isPresent()) {
            recordProcessed(event.eventId());
            return;
        }

        Instant now = Instant.now();
        Payment payment = new Payment();
        payment.initialize(UUID.randomUUID().toString(), request.orderId(), request.userId(), request.amount(), now);
        paymentRepository.insert(payment);

        PaymentStatus result = paymentProcessor.process(payment);
        payment.complete(result, Instant.now());
        paymentRepository.save(payment);
        paymentOutboxService.appendResult(payment);
        recordProcessed(event.eventId());
    }

    private void recordProcessed(String eventId) {
        PaymentProcessedEvent processed = new PaymentProcessedEvent();
        processed.setEventId(eventId);
        processed.setProcessedAt(Instant.now());
        processedEventRepository.insert(processed);
    }
}
