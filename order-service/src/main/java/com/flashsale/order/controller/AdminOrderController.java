package com.flashsale.order.controller;

import com.flashsale.order.document.OrderStatus;
import com.flashsale.order.dto.OrderResponse;
import com.flashsale.order.dto.PageResponse;
import com.flashsale.order.service.AdminOrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN-only order monitoring (PRD 6.14, 16); access is enforced in OrderSecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {
    private final AdminOrderQueryService adminOrderQueryService;

    @GetMapping
    public PageResponse<OrderResponse> getOrders(@RequestParam(name = "eventId", required = false) String eventId,
                                                 @RequestParam(name = "status", required = false) OrderStatus status,
                                                 @RequestParam(name = "page", defaultValue = "0") int page,
                                                 @RequestParam(name = "size", defaultValue = "20") int size) {
        return adminOrderQueryService.findOrders(eventId, status, page, size);
    }
}
