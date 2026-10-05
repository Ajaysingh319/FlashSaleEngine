package com.flashsale.order.service;

import com.flashsale.order.document.Order;
import com.flashsale.order.document.OrderStatus;
import com.flashsale.order.repository.OrderRepository;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderExpiryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final OrderExpiryService service =
            new OrderExpiryService(orderRepository, mongoTemplate, Clock.fixed(NOW, ZoneOffset.UTC));

    private static Order pendingOrder(String orderId) {
        Order order = new Order();
        order.initialize(orderId, "user-1", "res-" + orderId, "event-1", "tt-1", 2, new BigDecimal("4999.00"),
                NOW.minusSeconds(700));
        order.setReservationExpiresAt(NOW.minusSeconds(100));
        return order;
    }

    private static UpdateResult modified(long count) {
        return UpdateResult.acknowledged(count, count, null);
    }

    @Test
    void looksUpOnlyPendingOrdersWhoseReservationHasExpired() {
        when(orderRepository.findTop100ByStatusAndReservationExpiresAtBefore(OrderStatus.PENDING_PAYMENT, NOW))
                .thenReturn(List.of());

        assertEquals(0, service.expireUnpaidOrders());

        verify(orderRepository).findTop100ByStatusAndReservationExpiresAtBefore(OrderStatus.PENDING_PAYMENT, NOW);
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void expiresEachDueOrderWithAConditionalAtomicUpdate() {
        when(orderRepository.findTop100ByStatusAndReservationExpiresAtBefore(OrderStatus.PENDING_PAYMENT, NOW))
                .thenReturn(List.of(pendingOrder("order-1")));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Order.class))).thenReturn(modified(1));

        assertEquals(1, service.expireUnpaidOrders());

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(query.capture(), update.capture(), eq(Order.class));
        Document criteria = query.getValue().getQueryObject();
        assertEquals("order-1", criteria.get("orderId"));
        assertEquals("PENDING_PAYMENT", criteria.get("status"));
        assertEquals("PENDING", criteria.get("paymentStatus"));
        assertEquals(new Document("$lt", NOW), criteria.get("reservationExpiresAt"));
        Document set = update.getValue().getUpdateObject().get("$set", Document.class);
        assertEquals("EXPIRED", set.get("status"));
        assertEquals(NOW, set.get("updatedAt"));
    }

    @Test
    void orderPaidOrCancelledMeanwhileIsNotExpired() {
        when(orderRepository.findTop100ByStatusAndReservationExpiresAtBefore(OrderStatus.PENDING_PAYMENT, NOW))
                .thenReturn(List.of(pendingOrder("order-1")));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Order.class))).thenReturn(modified(0));

        assertEquals(0, service.expireUnpaidOrders());
    }

    @Test
    void oneFailingOrderDoesNotStopTheOthers() {
        when(orderRepository.findTop100ByStatusAndReservationExpiresAtBefore(OrderStatus.PENDING_PAYMENT, NOW))
                .thenReturn(List.of(pendingOrder("order-1"), pendingOrder("order-2")));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Order.class)))
                .thenThrow(new RuntimeException("write failed"))
                .thenReturn(modified(1));

        assertEquals(1, service.expireUnpaidOrders());
        verify(mongoTemplate, times(2)).updateFirst(any(Query.class), any(Update.class), eq(Order.class));
    }

    @Test
    void expiredOrderCannotBeCancelledOrPaidAfterwards() {
        Order order = pendingOrder("order-1");
        order.expire(NOW);

        assertEquals(OrderStatus.EXPIRED, order.getStatus());
        assertThrows(RuntimeException.class, order::requireCancellable);
        assertThrows(RuntimeException.class, () -> order.applyPaymentResult("pay-1", true, NOW));
    }
}
