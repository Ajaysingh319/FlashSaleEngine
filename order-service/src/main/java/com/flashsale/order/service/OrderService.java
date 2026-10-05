package com.flashsale.order.service;

import com.flashsale.order.document.Order;
import com.flashsale.order.document.Idempotency;
import com.flashsale.order.document.OrderStatus;
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
import com.flashsale.order.exception.InvalidOrderStateException;
import com.flashsale.order.exception.ReservationLifecycleConflictException;
import com.flashsale.order.repository.IdempotencyRepository;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.order.outbox.OrderOutboxService;
import org.springframework.dao.DuplicateKeyException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.math.BigDecimal;
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
    private final ReservationServiceClient reservationServiceClient;
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

        // The price was snapshotted on the reservation (TDD 16); the order charges exactly that amount.
        BigDecimal unitPrice = reservation.getUnitPrice();
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
        order.setReservationExpiresAt(reservation.getExpiresAt());
        try {
            orderRepository.insert(order);
        } catch (DuplicateKeyException exception) {
            throw new ReservationAlreadyUsedException(reservation.getReservationId());
        }
        orderOutboxService.appendCreated(order);
        return toResponse(order);
    }

    /**
     * User-requested cancellation of an order awaiting payment (PRD 6.13). The reservation is released first, so
     * reserved tickets return to inventory; the order update and its event then share one local transaction.
     * Idempotent: cancelling an already cancelled order returns it unchanged.
     */
    @Transactional
    public OrderResponse cancelOrder(String orderId, String userId) {
        Order order = orderForUser(orderId, userId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return toResponse(order);
        }
        order.requireCancellable();
        releaseReservation(order);
        order.cancel(Instant.now());
        orderRepository.save(order);
        orderOutboxService.appendCancelled(order);
        return toResponse(order);
    }

    /**
     * Releases the order's reservation. If Reservation reports it is no longer ACTIVE, its actual status decides:
     * CANCELLED or EXPIRED means nothing is held any more; CONFIRMED means the tickets were sold to a completed
     * payment, so the order must not be cancelled.
     */
    private void releaseReservation(Order order) {
        try {
            reservationServiceClient.releaseReservation(order.getReservationId(), order.getOrderId(), order.getUserId());
        } catch (ReservationLifecycleConflictException conflict) {
            String status = reservationServiceClient.getReservationById(order.getReservationId()).getStatus();
            if (!"CANCELLED".equals(status) && !"EXPIRED".equals(status)) {
                throw new InvalidOrderStateException("Order cannot be cancelled: its reservation is " + status);
            }
        }
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
        if (!hasConsistentPrice(reservation)) {
            throw new ReservationNotActiveException("Reservation has no valid price snapshot");
        }
    }

    /** unitPrice must be positive and amount must equal unitPrice x quantity, so the order total matches the hold. */
    private static boolean hasConsistentPrice(ReservationResponse reservation) {
        BigDecimal unitPrice = reservation.getUnitPrice();
        BigDecimal amount = reservation.getAmount();
        return unitPrice != null && unitPrice.signum() > 0 && amount != null
                && amount.compareTo(unitPrice.multiply(BigDecimal.valueOf(reservation.getQuantity()))) == 0;
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
        return RequestFingerprint.of(request.getReservationId());
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
}
