package com.flashsale.order.service;

import com.flashsale.order.document.Idempotency;
import com.flashsale.order.document.Order;
import com.flashsale.order.dto.OrderRequest;
import com.flashsale.order.dto.OrderResponse;
import com.flashsale.order.dto.ReservationResponse;
import com.flashsale.order.exception.IdempotencyConflictException;
import com.flashsale.order.repository.IdempotencyRepository;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.order.outbox.OrderOutboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock private OrderRepository orderRepository;
    @Mock private IdempotencyRepository idempotencyRepository;
    @Mock private ReservationServiceClient reservationServiceClient;
    @Mock private CatalogServiceClient catalogServiceClient;
    @Mock private OrderOutboxService orderOutboxService;
    @InjectMocks private OrderService orderService;

    @Test
    void createsOrderFromActiveOwnedReservationAndSnapshotsCatalogPrice() {
        OrderRequest request = request("reservation-1");
        ReservationResponse reservation = activeReservation();
        when(idempotencyRepository.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.empty());
        when(reservationServiceClient.getReservationById("reservation-1")).thenReturn(reservation);
        when(catalogServiceClient.getUnitPrice("event-1", "ticket-type-1")).thenReturn(new BigDecimal("19.95"));
        when(orderRepository.existsByReservationId("reservation-1")).thenReturn(false);
        when(orderRepository.insert(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createOrder("user-1", "key-1", request);

        assertEquals(new BigDecimal("19.95"), response.getUnitPrice());
        assertEquals(new BigDecimal("39.90"), response.getTotalAmount());
        assertEquals("PENDING_PAYMENT", response.getStatus());
        assertEquals("PENDING", response.getPaymentStatus());
        ArgumentCaptor<Order> order = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).insert(order.capture());
        assertEquals("reservation-1", order.getValue().getReservationId());
        verify(orderOutboxService).appendCreated(order.getValue());
    }

    @Test
    void replaysTheOriginalOrderForTheSameUserKeyAndRequest() {
        Idempotency idempotency = new Idempotency();
        idempotency.setRequestFingerprint(fingerprint("reservation-1"));
        idempotency.setOrderId("order-1");
        Order order = new Order();
        order.initialize("order-1", "user-1", "reservation-1", "event-1", "ticket-type-1", 2,
                new BigDecimal("19.95"), Instant.now());
        when(idempotencyRepository.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.of(idempotency));
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(order));

        OrderResponse response = orderService.createOrder("user-1", "key-1", request("reservation-1"));

        assertEquals("order-1", response.getOrderId());
        verifyNoInteractions(reservationServiceClient, catalogServiceClient);
    }

    @Test
    void rejectsAReusedKeyForADifferentRequest() {
        Idempotency idempotency = new Idempotency();
        idempotency.setRequestFingerprint("other-fingerprint");
        when(idempotencyRepository.findByUserIdAndIdempotencyKey(eq("user-1"), eq("key-1")))
                .thenReturn(Optional.of(idempotency));

        assertThrows(IdempotencyConflictException.class,
                () -> orderService.createOrder("user-1", "key-1", request("reservation-1")));
    }

    private OrderRequest request(String reservationId) {
        OrderRequest request = new OrderRequest();
        request.setReservationId(reservationId);
        return request;
    }

    private ReservationResponse activeReservation() {
        ReservationResponse response = new ReservationResponse();
        response.setReservationId("reservation-1");
        response.setUserId("user-1");
        response.setEventId("event-1");
        response.setTicketTypeId("ticket-type-1");
        response.setQuantity(2);
        response.setStatus("ACTIVE");
        response.setExpiresAt(Instant.now().plusSeconds(600));
        return response;
    }

    private String fingerprint(String reservationId) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(reservationId.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
