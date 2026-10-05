package com.flashsale.order.controller;

import com.flashsale.order.dto.OrderRequest;
import com.flashsale.order.dto.OrderResponse;
import com.flashsale.order.dto.PaymentInitiationRequest;
import com.flashsale.order.dto.PaymentInitiationResponse;
import com.flashsale.order.service.OrderPaymentService;
import com.flashsale.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private final OrderService orderService;
    private final OrderPaymentService orderPaymentService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            Principal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OrderRequest request) {
        OrderResponse response = orderService.createOrder(principal.getName(), idempotencyKey, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable("orderId") String orderId, Principal principal) {
        String userId = principal.getName();
        OrderResponse response = orderService.getOrder(orderId, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable("orderId") String orderId, Principal principal) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId, principal.getName()));
    }

    @GetMapping("/me")
    public ResponseEntity<List<OrderResponse>> getMyOrders(Principal principal) {
        String userId = principal.getName();
        List<OrderResponse> responses = orderService.getMyOrders(userId);
        return ResponseEntity.ok(responses);
    }

    /** PRD 6.8 / TDD 48: accepted immediately; the payment result arrives asynchronously. */
    @PostMapping("/{orderId}/payment")
    public ResponseEntity<PaymentInitiationResponse> initiatePayment(
            @PathVariable("orderId") String orderId,
            Principal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentInitiationRequest request) {
        return ResponseEntity.accepted()
                .body(orderPaymentService.initiatePayment(principal.getName(), idempotencyKey, orderId, request));
    }
}
