package com.flashsale.order.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flashsale.order.dto.PaymentResultEnvelope;
import com.flashsale.order.exception.InvalidOrderStateException;
import com.flashsale.order.exception.ReservationServiceUnavailableException;
import com.flashsale.order.service.OrderPaymentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentResultListenerTest {

    private final OrderPaymentService orderPaymentService = mock(OrderPaymentService.class);
    private final PaymentResultListener listener =
            new PaymentResultListener(new ObjectMapper().registerModule(new JavaTimeModule()), orderPaymentService);

    private static String event(String eventType, String status) {
        return """
                {"eventId":"evt-1","eventType":"%s","timestamp":"2026-10-05T10:00:00Z","aggregateType":"PAYMENT",
                 "aggregateId":"order-1","payload":{"paymentId":"pay-1","orderId":"order-1","userId":"user-1",
                 "amount":9998.00,"status":"%s","timestamp":"2026-10-05T10:00:00Z"}}
                """.formatted(eventType, status);
    }

    @Test
    void timeoutIsHandledAsAFailedPayment() {
        listener.onPaymentResult(event("payment.failed", "TIMEOUT"));

        ArgumentCaptor<PaymentResultEnvelope> handled = ArgumentCaptor.forClass(PaymentResultEnvelope.class);
        verify(orderPaymentService).processPaymentResult(handled.capture());
        assertEquals("TIMEOUT", handled.getValue().payload().status());
    }

    @Test
    void declinedAndSuccessfulResultsAreHandled() {
        listener.onPaymentResult(event("payment.failed", "FAILED"));
        listener.onPaymentResult(event("payment.completed", "SUCCESS"));

        verify(orderPaymentService, times(2)).processPaymentResult(any());
    }

    @Test
    void refundConfirmationIsHandled() {
        listener.onPaymentResult(event("payment.refunded", "REFUNDED"));

        verify(orderPaymentService).processPaymentResult(any());
    }

    @Test
    void malformedOrContradictoryEventsAreRejectedForTheDeadLetterTopic() {
        for (String message : List.of("{not json", event("payment.failed", "SUCCESS"),
                event("payment.completed", "TIMEOUT"), event("payment.completed", "FAILED"),
                event("payment.refunded", "SUCCESS"), event("payment.unknown", "SUCCESS"))) {
            assertThrows(InvalidEventException.class, () -> listener.onPaymentResult(message));
        }
        verifyNoInteractions(orderPaymentService);
    }

    @Test
    void resultThatDoesNotMatchItsOrderIsRejectedForTheDeadLetterTopic() {
        doThrow(new IllegalArgumentException("amount mismatch")).when(orderPaymentService).processPaymentResult(any());

        assertThrows(InvalidEventException.class, () -> listener.onPaymentResult(event("payment.completed", "SUCCESS")));
    }

    @Test
    void staleResultForAnAlreadySettledOrderIsSkipped() {
        doThrow(new InvalidOrderStateException("already confirmed")).when(orderPaymentService).processPaymentResult(any());

        assertDoesNotThrow(() -> listener.onPaymentResult(event("payment.completed", "SUCCESS")));
    }

    @Test
    void transientFailureIsRethrownUnchangedSoItIsRetried() {
        ReservationServiceUnavailableException down = new ReservationServiceUnavailableException("down");
        doThrow(down).when(orderPaymentService).processPaymentResult(any());

        assertSame(down, assertThrows(ReservationServiceUnavailableException.class,
                () -> listener.onPaymentResult(event("payment.completed", "SUCCESS"))));
    }
}
