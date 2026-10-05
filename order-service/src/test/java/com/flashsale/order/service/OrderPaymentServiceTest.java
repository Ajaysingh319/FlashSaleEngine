package com.flashsale.order.service;

import com.flashsale.order.document.Idempotency;
import com.flashsale.order.document.Order;
import com.flashsale.order.document.OrderStatus;
import com.flashsale.order.document.PaymentStatus;
import com.flashsale.order.document.ProcessedEvent;
import com.flashsale.order.dto.PaymentInitiationRequest;
import com.flashsale.order.dto.PaymentInitiationResponse;
import com.flashsale.order.dto.PaymentResultEnvelope;
import com.flashsale.order.dto.PaymentResultPayload;
import com.flashsale.order.exception.IdempotencyConflictException;
import com.flashsale.order.exception.InvalidOrderStateException;
import com.flashsale.order.exception.ReservationExpiredException;
import com.flashsale.order.exception.ReservationLifecycleConflictException;
import com.flashsale.order.exception.ReservationNotFoundException;
import com.flashsale.order.exception.ReservationServiceUnavailableException;
import com.flashsale.order.exception.UnauthorizedOrderAccessException;
import com.flashsale.order.outbox.OrderOutboxService;
import com.flashsale.order.repository.IdempotencyRepository;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.order.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OrderPaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private static final PaymentInitiationRequest CARD = new PaymentInitiationRequest("MOCK_CARD");

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final IdempotencyRepository idempotencyRepository = mock(IdempotencyRepository.class);
    private final ProcessedEventRepository processedEventRepository = mock(ProcessedEventRepository.class);
    private final ReservationServiceClient reservationServiceClient = mock(ReservationServiceClient.class);
    private final OrderOutboxService orderOutboxService = mock(OrderOutboxService.class);
    private final OrderPaymentService service = new OrderPaymentService(orderRepository, idempotencyRepository,
            processedEventRepository, reservationServiceClient, orderOutboxService, Clock.fixed(NOW, ZoneOffset.UTC));

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.initialize("order-1", "user-1", "res-1", "event-1", "tt-1", 2, new BigDecimal("4999.00"), NOW.minusSeconds(60));
        order.setReservationExpiresAt(NOW.plusSeconds(540));
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(order));
        when(idempotencyRepository.findByUserIdAndIdempotencyKey(anyString(), anyString())).thenReturn(Optional.empty());
    }

    // --- Initiation ---

    @Test
    void initiationStartsPaymentAndEmitsPaymentRequested() {
        PaymentInitiationResponse response = service.initiatePayment("user-1", "key-1", "order-1", CARD);

        assertEquals("PROCESSING", response.status());
        assertNotNull(response.paymentId());
        assertEquals(response.paymentId(), order.getPaymentId());
        assertEquals("MOCK_CARD", order.getPaymentMethod());
        assertEquals(PaymentStatus.PROCESSING, order.getPaymentStatus());
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        ArgumentCaptor<Idempotency> claim = ArgumentCaptor.forClass(Idempotency.class);
        verify(idempotencyRepository).insert(claim.capture());
        assertEquals("order-1", claim.getValue().getOrderId());
        verify(orderRepository).save(order);
        verify(orderOutboxService).appendPaymentRequested(order);
    }

    @Test
    void secondInitiationReturnsTheSamePaymentWithoutStartingAnother() {
        String first = service.initiatePayment("user-1", "key-1", "order-1", CARD).paymentId();
        reset(orderOutboxService, idempotencyRepository);
        when(idempotencyRepository.findByUserIdAndIdempotencyKey(anyString(), anyString())).thenReturn(Optional.empty());

        PaymentInitiationResponse again = service.initiatePayment("user-1", "key-2", "order-1", CARD);

        assertEquals(first, again.paymentId());
        verifyNoInteractions(orderOutboxService);
        verify(idempotencyRepository, never()).insert(any(Idempotency.class));
    }

    @Test
    void sameKeyReplaysAndDifferentRequestWithSameKeyIsAConflict() {
        order.startPayment("pay-1", "MOCK_CARD", NOW);
        Idempotency previous = new Idempotency();
        previous.setOrderId("order-1");
        previous.setRequestFingerprint(RequestFingerprint.of("payment\norder-1\nMOCK_CARD"));
        when(idempotencyRepository.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.of(previous));

        assertEquals("pay-1", service.initiatePayment("user-1", "key-1", "order-1", CARD).paymentId());
        assertThrows(IdempotencyConflictException.class,
                () -> service.initiatePayment("user-1", "key-1", "order-2", CARD));
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderOutboxService);
    }

    @Test
    void initiationPreconditionsAreEnforced() {
        assertThrows(IllegalArgumentException.class, () -> service.initiatePayment("user-1", " ", "order-1", CARD));
        assertThrows(UnauthorizedOrderAccessException.class, () -> service.initiatePayment("user-2", "key-1", "order-1", CARD));

        order.setReservationExpiresAt(NOW);
        assertThrows(ReservationExpiredException.class, () -> service.initiatePayment("user-1", "key-1", "order-1", CARD));

        order.setReservationExpiresAt(NOW.plusSeconds(60));
        order.cancel(NOW);
        assertThrows(InvalidOrderStateException.class, () -> service.initiatePayment("user-1", "key-1", "order-1", CARD));

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderOutboxService);
    }

    @Test
    void startedPaymentBlocksCancellationAndExpiry() {
        order.startPayment("pay-1", "MOCK_CARD", NOW);

        assertThrows(InvalidOrderStateException.class, order::requireCancellable);
        assertThrows(InvalidOrderStateException.class, () -> order.expire(NOW));
    }

    // --- Results ---

    private static PaymentResultEnvelope result(String eventType, String paymentId, String status) {
        return new PaymentResultEnvelope("evt-1", eventType, NOW, "PAYMENT", "order-1",
                new PaymentResultPayload(paymentId, "order-1", "user-1", new BigDecimal("9998.00"), status, NOW));
    }

    @Test
    void successConfirmsReservationThenOrder() {
        order.startPayment("pay-1", "MOCK_CARD", NOW);

        service.processPaymentResult(result("payment.completed", "pay-1", "SUCCESS"));

        var inOrder = inOrder(reservationServiceClient, orderRepository, orderOutboxService);
        inOrder.verify(reservationServiceClient).confirmReservation("res-1", "order-1", "user-1");
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(orderOutboxService).appendConfirmed(order);
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(PaymentStatus.SUCCEEDED, order.getPaymentStatus());
        verify(processedEventRepository).insert(any(ProcessedEvent.class));
    }

    @Test
    void failureReleasesReservationThenFailsOrder() {
        order.startPayment("pay-1", "MOCK_CARD", NOW);

        service.processPaymentResult(result("payment.failed", "pay-1", "FAILED"));

        verify(reservationServiceClient).cancelAfterPaymentFailure("res-1", "order-1", "user-1");
        verify(orderOutboxService).appendPaymentFailed(order);
        assertEquals(OrderStatus.PAYMENT_FAILED, order.getStatus());
        assertEquals(PaymentStatus.FAILED, order.getPaymentStatus());
    }

    // --- Compensation (refund) ---

    @Test
    void successForTicketsThatAreGoneCancelsOrderAndRequestsRefund() {
        for (RuntimeException ticketsGone : new RuntimeException[]{
                new ReservationLifecycleConflictException("reservation expired"),
                new ReservationNotFoundException("reservation missing")}) {
            reset(orderRepository, orderOutboxService, reservationServiceClient, processedEventRepository);
            setUp();
            order.startPayment("pay-1", "MOCK_CARD", NOW);
            doThrow(ticketsGone).when(reservationServiceClient).confirmReservation("res-1", "order-1", "user-1");

            service.processPaymentResult(result("payment.completed", "pay-1", "SUCCESS"));

            assertEquals(OrderStatus.CANCELLED, order.getStatus());
            assertEquals(PaymentStatus.REFUND_REQUESTED, order.getPaymentStatus());
            verify(orderRepository).save(order);
            verify(orderOutboxService).appendRefundRequested(order);
            verify(orderOutboxService, never()).appendConfirmed(any());
            verify(processedEventRepository).insert(any(ProcessedEvent.class));
        }
    }

    @Test
    void unreachableReservationServiceIsRetriedNotRefunded() {
        order.startPayment("pay-1", "MOCK_CARD", NOW);
        doThrow(new ReservationServiceUnavailableException("down"))
                .when(reservationServiceClient).confirmReservation("res-1", "order-1", "user-1");

        assertThrows(ReservationServiceUnavailableException.class,
                () -> service.processPaymentResult(result("payment.completed", "pay-1", "SUCCESS")));

        assertEquals(PaymentStatus.PROCESSING, order.getPaymentStatus());
        verifyNoInteractions(orderOutboxService);
        verify(processedEventRepository, never()).insert(any(ProcessedEvent.class));
    }

    @Test
    void failedPaymentStillSettlesWhenReservationWasAlreadyReleased() {
        order.startPayment("pay-1", "MOCK_CARD", NOW);
        doThrow(new ReservationLifecycleConflictException("already expired"))
                .when(reservationServiceClient).cancelAfterPaymentFailure("res-1", "order-1", "user-1");

        service.processPaymentResult(result("payment.failed", "pay-1", "TIMEOUT"));

        assertEquals(OrderStatus.PAYMENT_FAILED, order.getStatus());
        verify(orderOutboxService).appendPaymentFailed(order);
    }

    @Test
    void refundedOrderIgnoresAnyLaterResult() {
        order.startPayment("pay-1", "MOCK_CARD", NOW);
        order.cancelWithRefund(NOW);

        service.processPaymentResult(result("payment.completed", "pay-1", "SUCCESS"));

        verifyNoInteractions(reservationServiceClient, orderOutboxService);
        assertEquals(PaymentStatus.REFUND_REQUESTED, order.getPaymentStatus());
    }

    @Test
    void duplicateResultEventIsIgnored() {
        when(processedEventRepository.existsByEventId("evt-1")).thenReturn(true);

        service.processPaymentResult(result("payment.completed", "pay-1", "SUCCESS"));

        verifyNoInteractions(reservationServiceClient, orderOutboxService);
        verify(orderRepository, never()).findByOrderId(anyString());
    }

    @Test
    void resultForAnotherPaymentNeverTouchesReservation() {
        order.startPayment("pay-1", "MOCK_CARD", NOW);

        assertThrows(IllegalArgumentException.class,
                () -> service.processPaymentResult(result("payment.completed", "pay-other", "SUCCESS")));
        verifyNoInteractions(reservationServiceClient, orderOutboxService);
        assertEquals(PaymentStatus.PROCESSING, order.getPaymentStatus());
    }
}
