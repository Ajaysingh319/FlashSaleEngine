package com.flashsale.order.service;

import com.flashsale.order.config.ReportingReads;
import com.flashsale.order.document.Order;
import com.flashsale.order.document.OrderStatus;
import com.flashsale.order.dto.OrderResponse;
import com.flashsale.order.dto.PageResponse;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AdminOrderQueryServiceTest {
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ReportingReads reportingReads = mock(ReportingReads.class);
    private final AdminOrderQueryService service = new AdminOrderQueryService(reportingReads);

    AdminOrderQueryServiceTest() {
        when(reportingReads.mongo()).thenReturn(mongoTemplate);
    }

    private static Order order() {
        Order order = new Order();
        order.initialize("order-1", "user-1", "res-1", "event-1", "tt-1", 2, new BigDecimal("4999.00"), Instant.EPOCH);
        return order;
    }

    @Test
    void filtersByEventAndStatusNewestFirstAndPaged() {
        when(mongoTemplate.find(any(Query.class), eq(Order.class))).thenReturn(Collections.nCopies(20, order()));
        when(mongoTemplate.count(any(Query.class), eq(Order.class))).thenReturn(41L);

        PageResponse<OrderResponse> page = service.findOrders("event-1", OrderStatus.CONFIRMED, 1, 20);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(Order.class));
        assertEquals(new Document("eventId", "event-1").append("status", OrderStatus.CONFIRMED), query.getValue().getQueryObject());
        assertEquals(new Document("createdAt", -1), query.getValue().getSortObject());
        assertEquals(20, query.getValue().getSkip());
        assertEquals(20, query.getValue().getLimit());
        assertEquals("order-1", page.content().get(0).getOrderId());
        assertEquals(41, page.totalElements());
        assertEquals(3, page.totalPages());
    }

    @Test
    void noFiltersListsAllOrders() {
        when(mongoTemplate.find(any(Query.class), eq(Order.class))).thenReturn(List.of());

        service.findOrders(" ", null, 0, 20);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(Order.class));
        assertTrue(query.getValue().getQueryObject().isEmpty());
    }

    @Test
    void partialPageNeedsNoCountQuery() {
        when(mongoTemplate.find(any(Query.class), eq(Order.class))).thenReturn(List.of(order()));

        assertEquals(21, service.findOrders(null, null, 1, 20).totalElements());
        verify(mongoTemplate, never()).count(any(Query.class), eq(Order.class));
    }

    @Test
    void pageSizeIsBoundedSoAListCanNeverScanEverything() {
        for (int[] invalid : new int[][]{{-1, 20}, {0, 0}, {0, 101}}) {
            assertThrows(IllegalArgumentException.class, () -> service.findOrders(null, null, invalid[0], invalid[1]));
        }
        verifyNoInteractions(mongoTemplate);
    }
}
