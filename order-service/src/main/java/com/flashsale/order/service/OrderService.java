package com.flashsale.order.service;

import com.flashsale.order.document.Order;
import com.flashsale.order.document.OrderStatus;
import com.flashsale.order.dto.OrderRequest;
import com.flashsale.order.dto.OrderResponse;
import com.flashsale.order.exception.OrderNotFoundException;
import com.flashsale.order.exception.UnauthorizedOrderAccessException;
import com.flashsale.order.exception.ReservationNotActiveException;
import com.flashsale.order.exception.ReservationNotFoundException;
import com.flashsale.order.exception.ReservationOwnershipException;
import com.flashsale.order.exception.ReservationServiceUnavailableException;
import com.flashsale.order.exception.ReservationExpiredException;
import com.flashsale.order.repository.IdempotencyRepository;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.order.repository.OutboxEventRepository;
import com.flashsale.order.repository.ProcessedEventRepository;
import com.flashsale.reservation.dto.ReservationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ReservationServiceClient reservationServiceClient;

    @Transactional
    public OrderResponse createOrder(String userId, OrderRequest request) {
        // Validate reservation via reservation service
        ReservationResponse reservation = reservationServiceClient.getReservationById(request.getReservationId());

        // Check ownership
        if (!reservation.getUserId().equals(userId)) {
            throw new ReservationOwnershipException("Reservation does not belong to user");
        }

        // Check active status
        if (!"PENDING".equalsIgnoreCase(reservation.getStatus())) {
            throw new ReservationNotActiveException("Reservation is not active (status: " + reservation.getStatus() + ")");
        }

        // Check expiration
        if (Instant.now().isAfter(reservation.getExpiresAt())) {
            throw new ReservationExpiredException("Reservation has expired");
        }

        // Build order
        Order order = new Order();
        order.setOrderId(UUID.randomUUID().toString());
        order.setUserId(userId);
        order.setReservationId(reservation.getId());
        order.setEventId(reservation.getEventId());
        order.setTicketTypeId(reservation.getTicketTypeId());
        order.setQuantity(reservation.getQuantity());
        // Amount calculation: for now placeholder; in real system would call pricing service
        order.setAmount(0.0);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        return toResponse(order);
    }

    public OrderResponse getOrder(String orderId, String userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        if (!order.getUserId().equals(userId)) {
            throw new UnauthorizedOrderAccessException("User not authorized to access this order");
        }
        return toResponse(order);
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
        response.setAmount(order.getAmount());
        response.setStatus(order.getStatus().toString());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        return response;
    }
}