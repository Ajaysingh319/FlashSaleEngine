package com.flashsale.order.service;

import com.flashsale.order.document.Order;
import com.flashsale.order.document.Idempotency;
import com.flashsale.order.document.ProcessedEvent;
import com.flashsale.order.document.OrderStatus;
import com.flashsale.order.dto.PaymentResultEnvelope;
import com.flashsale.order.dto.PaymentResultPayload;
import com.flashsale.order.dto.OrderRequest;
import com.flashsale.order.dto.OrderResponse;
import com.flashsale.order.dto.ReservationResponse;
import com.flashsale.order.exception.OrderNotFoundException;
import com.flashsale.order.exception.UnauthorizedOrderAccessException;
import com.flashsale.order.exception.IdempotencyConflictException;
import com.flashsale.order.exception.ReservationAlreadyUsedException;
import com.flashsale.order.exception.ReservationNotActiveException;
import com.flashsale.order.exception.ReservationNotFoundException;
import com.flashsale.order.exception.ReservationOwnershipException;
import com.flashsale.order.exception.ReservationServiceUnavailableException;
import com.flashsale.order.exception.ReservationExpiredException;
import com.flashsale.order.repository.IdempotencyRepository;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.order.repository.ProcessedEventRepository;
import com.flashsale.order.outbox.OrderOutboxService;
import org.springframework.dao.DuplicateKeyException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for order operations.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ReservationServiceClient reservationServiceClient;
    private final CatalogServiceClient catalogServiceClient;
    private final OrderOutboxService orderOutboxService;

    @Transactional
    public OrderResponse createOrder(String userId, String idempotencyKey, OrderRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }

        String fingerprint = fingerprint(request);
        Optional<Idempotency> existing = idempotencyRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return replayOrReject(existing.get(), fingerprint);
        }

        ReservationResponse reservation = reservationServiceClient.getReservationById(request.getReservationId());
        validateReservation(reservation, userId, request.getReservationId());

        BigDecimal unitPrice = catalogServiceClient.getUnitPrice(reservation.getEventId(), reservation.getTicketTypeId());
        Instant now = Instant.now();
        String orderId = UUID.randomUUID().toString();
        Idempotency idempotency = new Idempotency();
        idempotency.setUserId(userId);
        idempotency.setIdempotencyKey(idempotencyKey);
        idempotency.setRequestFingerprint(fingerprint);
        idempotency.setOrderId(orderId);
        idempotency.setCreatedAt(now);

        try {
            // The unique key is the concurrent-request claim; both the claim and order are local transaction work.
            idempotencyRepository.insert(idempotency);
        } catch (DuplicateKeyException exception) {
            Idempotency persisted = idempotencyRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> exception);
            return replayOrReject(persisted, fingerprint);
        }

        if (orderRepository.existsByReservationId(reservation.getReservationId())) {
            throw new ReservationAlreadyUsedException(reservation.getReservationId());
        }

        Order order = new Order();
        order.initialize(orderId, userId, reservation.getReservationId(), reservation.getEventId(),
                reservation.getTicketTypeId(), reservation.getQuantity(), unitPrice, now);
        try {
            orderRepository.insert(order);
        } catch (DuplicateKeyException exception) {
            throw new ReservationAlreadyUsedException(reservation.getReservationId());
        }
        orderOutboxService.appendCreated(order);
        return toResponse(order);
    }

    /** User-requested cancellation; the order update and its event share one local transaction. */
    @Transactional
    public OrderResponse cancelOrder(String orderId, String userId) {
        Order order = orderForUser(orderId, userId);
        order.cancel(Instant.now());
        orderRepository.save(order);
        orderOutboxService.appendCancelled(order);
        return toResponse(order);
    }

    /** Reserved for a future Payment-result consumer; it deliberately contains no Kafka consumption logic. */
    @Transactional
    public OrderResponse confirmPayment(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        order.confirmPayment(Instant.now());
        orderRepository.save(order);
        orderOutboxService.appendConfirmed(order);
        return toResponse(order);
    }

    /** Reserved for a future Payment-result consumer; it deliberately contains no Kafka consumption logic. */
    @Transactional
    public OrderResponse failPayment(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        order.failPayment(Instant.now());
        orderRepository.save(order);
        orderOutboxService.appendPaymentFailed(order);
        return toResponse(order);
    }

    /** Handles one validated Payment result together with its idempotency record and lifecycle event. */
    @Transactional
    public void processPaymentResult(PaymentResultEnvelope event) {
        if (processedEventRepository.existsByEventId(event.eventId())) return;

        PaymentResultPayload result = event.payload();
        Order order = orderRepository.findByOrderId(result.orderId())
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + result.orderId()));
        if (!order.getUserId().equals(result.userId())) {
            throw new IllegalArgumentException("Payment result user does not match the order");
        }
        if (order.getTotalAmount() == null || result.amount() == null
                || order.getTotalAmount().compareTo(result.amount()) != 0) {
            throw new IllegalArgumentException("Payment result amount does not match the order");
        }
        if (order.getPaymentId() != null && !order.getPaymentId().equals(result.paymentId())) {
            throw new IllegalArgumentException("Payment result paymentId does not match the order");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            recordProcessed(event);
            return;
        }

        boolean succeeded = "payment.completed".equals(event.eventType());
        if (succeeded) {
            // Call reservation service to confirm the reservation
            reservationServiceClient.confirmReservation(order.getReservationId(), order.getOrderId(), order.getUserId());
            // Only after successful reservation confirmation, update order to CONFIRMED
            order.applyPaymentResult(result.paymentId(), true, Instant.now());
        } else {
            // Call reservation service to release the reservation
            reservationServiceClient.cancelAfterPaymentFailure(order.getReservationId(), order.getOrderId(), order.getUserId());
            // After successful release, update order to PAYMENT_FAILED
            order.applyPaymentResult(result.paymentId(), false, Instant.now());
        }
        orderRepository.save(order);
        if (succeeded) orderOutboxService.appendConfirmed(order); else orderOutboxService.appendPaymentFailed(order);
        recordProcessed(event);
    }

    private void validateReservation(ReservationResponse reservation, String userId, String requestedReservationId) {
        if (reservation == null || reservation.getReservationId() == null
                || !requestedReservationId.equals(reservation.getReservationId())) {
            throw new ReservationNotFoundException("Reservation not found with id: " + requestedReservationId);
        }
        if (!userId.equals(reservation.getUserId())) {
            throw new ReservationOwnershipException("Reservation does not belong to user");
        }
        if (!"ACTIVE".equals(reservation.getStatus())) {
            throw new ReservationNotActiveException("Reservation is not active (status: " + reservation.getStatus() + ")");
        }
        if (reservation.getExpiresAt() == null || !reservation.getExpiresAt().isAfter(Instant.now())) {
            throw new ReservationExpiredException("Reservation has expired");
        }
        if (reservation.getEventId() == null || reservation.getTicketTypeId() == null
                || reservation.getQuantity() == null || reservation.getQuantity() < 1) {
            throw new ReservationNotActiveException("Reservation contains invalid order data");
        }
    }

    private OrderResponse replayOrReject(Idempotency idempotency, String fingerprint) {
        if (!fingerprint.equals(idempotency.getRequestFingerprint())) {
            throw new IdempotencyConflictException();
        }
        Order order = orderRepository.findByOrderId(idempotency.getOrderId())
                .orElseThrow(() -> new IllegalStateException("Idempotency record refers to a missing order"));
        return toResponse(order);
    }

    private String fingerprint(OrderRequest request) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(request.getReservationId().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public OrderResponse getOrder(String orderId, String userId) {
        return toResponse(orderForUser(orderId, userId));
    }

    public List<OrderResponse> getMyOrders(String userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private OrderResponse toResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getOrderId());
        response.setUserId(order.getUserId());
        response.setReservationId(order.getReservationId());
        response.setEventId(order.getEventId());
        response.setTicketTypeId(order.getTicketTypeId());
        response.setQuantity(order.getQuantity());
        response.setUnitPrice(order.getUnitPrice());
        response.setTotalAmount(order.getTotalAmount());
        response.setStatus(order.getStatus().toString());
        response.setPaymentStatus(order.getPaymentStatus().toString());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        return response;
    }

    private Order orderForUser(String orderId, String userId) {
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
        processed.setProcessedAt(Instant.now());
        processedEventRepository.insert(processed);
    }
}
