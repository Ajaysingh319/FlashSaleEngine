package com.flashsale.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flashsale.payment.dto.PaymentRequestEnvelope;
import com.flashsale.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentRequestListenerTest {

    /** Order's payment.requested event as serialized by OrderOutboxService (extra snapshot fields ignored). */
    private static final String ORDER_EVENT = """
            {"eventId":"evt-1","eventType":"payment.requested","timestamp":"2026-10-05T10:00:00Z",
             "aggregateType":"ORDER","aggregateId":"order-1",
             "payload":{"orderId":"order-1","userId":"user-1","reservationId":"res-1","eventId":"event-1",
                        "ticketTypeId":"tt-1","quantity":2,"unitPrice":4999.00,"totalAmount":9998.00,
                        "status":"PENDING_PAYMENT","paymentStatus":"PROCESSING",
                        "paymentId":"pay-1","paymentMethod":"MOCK_CARD"}}
            """;

    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentRequestListener listener =
            new PaymentRequestListener(new ObjectMapper().registerModule(new JavaTimeModule()), paymentService);

    @Test
    void parsesOrdersPaymentRequestedEvent() {
        listener.onPaymentRequested(ORDER_EVENT);

        ArgumentCaptor<PaymentRequestEnvelope> event = ArgumentCaptor.forClass(PaymentRequestEnvelope.class);
        verify(paymentService).processRequest(event.capture());
        assertEquals("pay-1", event.getValue().payload().paymentId());
        assertEquals("MOCK_CARD", event.getValue().payload().paymentMethod());
        assertEquals(0, new BigDecimal("9998.00").compareTo(event.getValue().payload().amount()));
    }

    @Test
    void refundRequestIsRoutedToRefundProcessing() {
        listener.onRefundRequested(ORDER_EVENT.replace("payment.requested", "payment.refund_requested"));

        ArgumentCaptor<PaymentRequestEnvelope> event = ArgumentCaptor.forClass(PaymentRequestEnvelope.class);
        verify(paymentService).processRefund(event.capture());
        assertEquals("pay-1", event.getValue().payload().paymentId());
        verify(paymentService, never()).processRequest(any());
    }

    @Test
    void malformedRefundRequestIsIgnored() {
        listener.onRefundRequested("{not json");

        verifyNoInteractions(paymentService);
    }

    @Test
    void malformedOrIncompleteEventsAreIgnored() {
        listener.onPaymentRequested("{not json");
        listener.onPaymentRequested(ORDER_EVENT.replace("\"paymentId\":\"pay-1\",", ""));
        listener.onPaymentRequested(ORDER_EVENT.replace(",\"paymentMethod\":\"MOCK_CARD\"", ""));

        verifyNoInteractions(paymentService);
    }
}
