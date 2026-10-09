package com.flashsale.order.service;

import com.flashsale.order.document.Idempotency;
import com.flashsale.order.document.Order;
import com.flashsale.order.dto.OrderRequest;
import com.flashsale.order.dto.OrderResponse;
import com.flashsale.order.dto.ReservationResponse;
import com.flashsale.order.exception.IdempotencyConflictException;
import com.flashsale.order.exception.InvalidOrderStateException;
import com.flashsale.order.exception.ReservationNotActiveException;
import com.flashsale.order.exception.ReservationLifecycleConflictException;
import com.flashsale.order.exception.ReservationServiceUnavailableException;
import com.flashsale.order.exception.UnauthorizedOrderAccessException;
import com.flashsale.order.repository.IdempotencyRepository;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.order.observability.OrderMetrics;
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
import java.util.List;
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
    @Mock private OrderOutboxService orderOutboxService;
    @Mock private OrderMetrics orderMetrics;
    @InjectMocks private OrderService orderService;

    @Test
    void createsOrderAtTheReservationsSnapshotPrice() {
        OrderRequest request = request("reservation-1");
        ReservationResponse reservation = activeReservation();
        when(idempotencyRepository.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.empty());
        when(reservationServiceClient.getReservationById("reservation-1")).thenReturn(reservation);
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
        assertNotNull(order.getValue().getReservationExpiresAt(), "order keeps the reservation expiry for order expiry");
        assertTrue(order.getValue().getReservationExpiresAt().isAfter(Instant.now()));
        verify(orderOutboxService).appendCreated(order.getValue());
        verify(orderMetrics).orderCreated();
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
        verifyNoInteractions(reservationServiceClient, orderMetrics);
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

    @Test
    void reservationWithoutAConsistentPriceCannotBecomeAnOrder() {
        ReservationResponse noPrice = activeReservation();
        noPrice.setUnitPrice(null);
        ReservationResponse wrongAmount = activeReservation();
        wrongAmount.setAmount(new BigDecimal("40.00"));
        ReservationResponse freeTickets = activeReservation();
        freeTickets.setUnitPrice(BigDecimal.ZERO);
        freeTickets.setAmount(BigDecimal.ZERO);

        for (ReservationResponse reservation : List.of(noPrice, wrongAmount, freeTickets)) {
            when(idempotencyRepository.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.empty());
            when(reservationServiceClient.getReservationById("reservation-1")).thenReturn(reservation);

            assertThrows(ReservationNotActiveException.class,
                    () -> orderService.createOrder("user-1", "key-1", request("reservation-1")));
        }
        verify(orderRepository, never()).insert(any(Order.class));
        verifyNoInteractions(orderOutboxService);
    }

    // --- Cancellation (PRD 6.13) ---

    private Order pendingOrder() {
        Order order = new Order();
        order.initialize("order-1", "user-1", "reservation-1", "event-1", "ticket-type-1", 2,
                new BigDecimal("4999.00"), Instant.now().minusSeconds(60));
        return order;
    }

    private static ReservationResponse reservationWithStatus(String status) {
        ReservationResponse response = new ReservationResponse();
        response.setReservationId("reservation-1");
        response.setUserId("user-1");
        response.setStatus(status);
        return response;
    }

    @Test
    void cancelReleasesReservationBeforeCancellingOrder() {
        Order order = pendingOrder();
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(order));
        when(reservationServiceClient.releaseReservation("reservation-1", "order-1", "user-1"))
                .thenReturn(reservationWithStatus("CANCELLED"));

        OrderResponse response = orderService.cancelOrder("order-1", "user-1");

        assertEquals("CANCELLED", response.getStatus());
        var inOrder = inOrder(reservationServiceClient, orderRepository, orderOutboxService);
        inOrder.verify(reservationServiceClient).releaseReservation("reservation-1", "order-1", "user-1");
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(orderOutboxService).appendCancelled(order);
    }

    @Test
    void cancellingAnAlreadyCancelledOrderIsIdempotent() {
        Order order = pendingOrder();
        order.cancel(Instant.now());
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(order));

        OrderResponse response = orderService.cancelOrder("order-1", "user-1");

        assertEquals("CANCELLED", response.getStatus());
        verifyNoInteractions(reservationServiceClient, orderOutboxService);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void confirmedOrFailedOrdersCannotBeCancelledAndReservationIsUntouched() {
        Order confirmed = pendingOrder();
        confirmed.startPayment("pay-1", "MOCK_CARD", Instant.now());
        confirmed.confirmPayment(Instant.now());
        Order failed = pendingOrder();
        failed.startPayment("pay-2", "MOCK_CARD", Instant.now());
        failed.failPayment(Instant.now());

        for (Order order : new Order[]{confirmed, failed}) {
            when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(order));
            assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder("order-1", "user-1"));
        }
        verifyNoInteractions(reservationServiceClient, orderOutboxService);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void reservationAlreadyExpiredOrCancelledStillLetsTheOrderBeCancelled() {
        for (String reservationStatus : new String[]{"EXPIRED", "CANCELLED"}) {
            reset(orderRepository, reservationServiceClient, orderOutboxService);
            Order order = pendingOrder();
            when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(order));
            when(reservationServiceClient.releaseReservation("reservation-1", "order-1", "user-1"))
                    .thenThrow(new ReservationLifecycleConflictException("not active"));
            when(reservationServiceClient.getReservationById("reservation-1"))
                    .thenReturn(reservationWithStatus(reservationStatus));

            OrderResponse response = orderService.cancelOrder("order-1", "user-1");

            assertEquals("CANCELLED", response.getStatus(), reservationStatus);
            verify(orderRepository).save(order);
            verify(orderOutboxService).appendCancelled(order);
        }
    }

    @Test
    void orderWhoseTicketsWereAlreadySoldIsNotCancelled() {
        Order order = pendingOrder();
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(order));
        when(reservationServiceClient.releaseReservation("reservation-1", "order-1", "user-1"))
                .thenThrow(new ReservationLifecycleConflictException("not active"));
        when(reservationServiceClient.getReservationById("reservation-1")).thenReturn(reservationWithStatus("CONFIRMED"));

        assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder("order-1", "user-1"));

        assertEquals("PENDING_PAYMENT", order.getStatus().name());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderOutboxService);
    }

    @Test
    void reservationServiceUnavailableLeavesOrderUnchangedForRetry() {
        Order order = pendingOrder();
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(order));
        when(reservationServiceClient.releaseReservation("reservation-1", "order-1", "user-1"))
                .thenThrow(new ReservationServiceUnavailableException("down"));

        assertThrows(ReservationServiceUnavailableException.class, () -> orderService.cancelOrder("order-1", "user-1"));

        assertEquals("PENDING_PAYMENT", order.getStatus().name());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderOutboxService);
    }

    @Test
    void anotherUsersOrderCannotBeCancelled() {
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(pendingOrder()));

        assertThrows(UnauthorizedOrderAccessException.class, () -> orderService.cancelOrder("order-1", "user-2"));
        verifyNoInteractions(reservationServiceClient, orderOutboxService);
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
        response.setUnitPrice(new BigDecimal("19.95"));
        response.setAmount(new BigDecimal("39.90"));
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
