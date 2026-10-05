package com.flashsale.order.service;

import com.flashsale.order.document.Idempotency;
import com.flashsale.order.document.Order;
import com.flashsale.order.document.ProcessedEvent;
import com.flashsale.order.dto.PaymentInitiationRequest;
import com.flashsale.order.dto.PaymentInitiationResponse;
import com.flashsale.order.dto.PaymentResultEnvelope;
import com.flashsale.order.dto.PaymentResultPayload;
import com.flashsale.order.exception.IdempotencyConflictException;
import com.flashsale.order.exception.OrderNotFoundException;
import com.flashsale.order.exception.ReservationExpiredException;
import com.flashsale.order.exception.ReservationLifecycleConflictException;
import com.flashsale.order.exception.ReservationNotFoundException;
import com.flashsale.order.exception.UnauthorizedOrderAccessException;
import com.flashsale.order.outbox.OrderOutboxService;
import com.flashsale.order.repository.IdempotencyRepository;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.order.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * An order's payment lifecycle: initiation (PRD 6.8, TDD 48) and settlement from Payment Service results
 * (TDD 35-36). Initiation only records the request and emits payment.requested; payment runs asynchronously.
 */
@Service
@RequiredArgsConstructor
public class OrderPaymentService {
    static final String PAYMENT_COMPLETED = "payment.completed";

    private final OrderRepository orderRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ReservationServiceClient reservationServiceClient;
    private final OrderOutboxService orderOutboxService;
    private final Clock clock;

    /**
     * Starts the order's single payment. Idempotent per Idempotency-Key (same key + same request replays, a
     * different request is a conflict) and per order (once started, every request returns the same payment).
     */
    @Transactional
    public PaymentInitiationResponse initiatePayment(String userId, String idempotencyKey, String orderId,
                                                     PaymentInitiationRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }
        String fingerprint = RequestFingerprint.of("payment\n" + orderId + "\n" + request.paymentMethod());
        Idempotency previous = idempotencyRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElse(null);
        if (previous != null) {
            return replayOrReject(previous, fingerprint, userId);
        }

        Order order = ownedOrder(orderId, userId);
        if (order.getPaymentId() != null) {
            return toResponse(order);
        }
        Instant now = clock.instant();
        if (order.getReservationExpiresAt() != null && !now.isBefore(order.getReservationExpiresAt())) {
            throw new ReservationExpiredException("Reservation has expired; the order can no longer be paid");
        }
        order.startPayment(UUID.randomUUID().toString(), request.paymentMethod(), now);
        claimIdempotencyKey(userId, idempotencyKey, fingerprint, orderId, now);
        orderRepository.save(order);
        orderOutboxService.appendPaymentRequested(order);
        return toResponse(order);
    }

    /** Applies one Payment Service result exactly once (processed_events), then emits the order's lifecycle event. */
    @Transactional
    public void processPaymentResult(PaymentResultEnvelope event) {
        if (processedEventRepository.existsByEventId(event.eventId())) return;

        PaymentResultPayload result = event.payload();
        Order order = orderRepository.findByOrderId(result.orderId())
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + result.orderId()));
        requireMatchingResult(order, result);
        if (order.isAwaitingPaymentResult()) {
            if (PAYMENT_COMPLETED.equals(event.eventType())) settleSuccess(order, result); else settleFailure(order, result);
        }
        recordProcessed(event);
    }

    /**
     * Confirms the reservation (reserved -> sold), then the order. If the reservation can no longer be confirmed
     * (expired, cancelled or gone) the customer has paid for tickets they cannot get: the order is cancelled and
     * a refund is requested (compensation, TDD 37). An unreachable Reservation Service propagates for a retry.
     */
    private void settleSuccess(Order order, PaymentResultPayload result) {
        try {
            reservationServiceClient.confirmReservation(order.getReservationId(), order.getOrderId(), order.getUserId());
        } catch (ReservationLifecycleConflictException | ReservationNotFoundException ticketsGone) {
            order.cancelWithRefund(clock.instant());
            orderRepository.save(order);
            orderOutboxService.appendRefundRequested(order);
            return;
        }
        order.applyPaymentResult(result.paymentId(), true, clock.instant());
        orderRepository.save(order);
        orderOutboxService.appendConfirmed(order);
    }

    /** Releases the reservation, then fails the order. A reservation already released (e.g. expired) holds nothing. */
    private void settleFailure(Order order, PaymentResultPayload result) {
        try {
            reservationServiceClient.releaseReservation(order.getReservationId(), order.getOrderId(), order.getUserId());
        } catch (ReservationLifecycleConflictException | ReservationNotFoundException alreadyReleased) {
            // Nothing is reserved any more; the order still records the failed payment.
        }
        order.applyPaymentResult(result.paymentId(), false, clock.instant());
        orderRepository.save(order);
        orderOutboxService.appendPaymentFailed(order);
    }

    private static void requireMatchingResult(Order order, PaymentResultPayload result) {
        if (!order.getUserId().equals(result.userId())) {
            throw new IllegalArgumentException("Payment result user does not match the order");
        }
        if (order.getTotalAmount() == null || result.amount() == null || order.getTotalAmount().compareTo(result.amount()) != 0) {
            throw new IllegalArgumentException("Payment result amount does not match the order");
        }
        if (!result.paymentId().equals(order.getPaymentId())) {
            throw new IllegalArgumentException("Payment result paymentId does not match the order");
        }
    }

    private PaymentInitiationResponse replayOrReject(Idempotency previous, String fingerprint, String userId) {
        if (!fingerprint.equals(previous.getRequestFingerprint())) {
            throw new IdempotencyConflictException();
        }
        return toResponse(ownedOrder(previous.getOrderId(), userId));
    }

    private void claimIdempotencyKey(String userId, String key, String fingerprint, String orderId, Instant now) {
        Idempotency claim = new Idempotency();
        claim.setUserId(userId);
        claim.setIdempotencyKey(key);
        claim.setRequestFingerprint(fingerprint);
        claim.setOrderId(orderId);
        claim.setCreatedAt(now);
        try {
            idempotencyRepository.insert(claim);
        } catch (DuplicateKeyException exception) {
            // A concurrent request with the same key won; the transaction rolls back and the client retries.
            throw new IdempotencyConflictException();
        }
    }

    private Order ownedOrder(String orderId, String userId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        if (!order.getUserId().equals(userId)) {
            throw new UnauthorizedOrderAccessException("User not authorized to access this order");
        }
        return order;
    }

    private void recordProcessed(PaymentResultEnvelope event) {
        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(event.eventId());
        processed.setEventType(event.eventType());
        processed.setProcessedAt(clock.instant());
        processedEventRepository.insert(processed);
    }

    private static PaymentInitiationResponse toResponse(Order order) {
        return new PaymentInitiationResponse(order.getPaymentId(), order.getOrderId(), order.getPaymentStatus().name());
    }
}
