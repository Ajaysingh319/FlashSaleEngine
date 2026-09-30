package com.flashsale.order.controller;

import com.flashsale.order.dto.OrderRequest;
import com.flashsale.order.dto.OrderResponse;
import com.flashsale.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            Principal principal,
            @RequestBody OrderRequest request) {
        String userId = principal.getName(); // assuming principal is userId
        OrderResponse response = orderService.createOrder(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId, Principal principal) {
        String userId = principal.getName();
        OrderResponse response = orderService.getOrder(orderId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<List<OrderResponse>> getMyOrders(Principal principal) {
        String userId = principal.getName();
        List<OrderResponse> responses = orderService.getMyOrders(userId);
        return ResponseEntity.ok(responses);
    }
}