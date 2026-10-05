package com.flashsale.order.service;

import com.flashsale.order.config.ReportingReads;
import com.flashsale.order.document.Order;
import com.flashsale.order.document.OrderStatus;
import com.flashsale.order.dto.OrderResponse;
import com.flashsale.order.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read-only order listing for administrators (PRD 6.14, GET /api/v1/admin/orders). Always paged and newest
 * first and read from secondaries, so a dashboard polling during a flash sale reads a bounded, index-backed slice
 * and never competes with the purchase flow on the primary.
 */
@Service
@RequiredArgsConstructor
public class AdminOrderQueryService {
    static final int MAX_PAGE_SIZE = 100;

    private final ReportingReads reportingReads;

    public PageResponse<OrderResponse> findOrders(String eventId, OrderStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        Query query = new Query();
        if (eventId != null && !eventId.isBlank()) query.addCriteria(Criteria.where("eventId").is(eventId));
        if (status != null) query.addCriteria(Criteria.where("status").is(status));
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        MongoTemplate mongo = reportingReads.mongo();
        List<OrderResponse> orders = mongo.find(Query.of(query).with(pageable), Order.class).stream()
                .map(OrderResponse::from)
                .toList();
        return PageResponse.from(PageableExecutionUtils.getPage(orders, pageable,
                () -> mongo.count(query, Order.class)));
    }
}
