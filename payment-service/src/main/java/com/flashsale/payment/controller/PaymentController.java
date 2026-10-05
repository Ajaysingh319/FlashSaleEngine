package com.flashsale.payment.controller;

import com.flashsale.payment.dto.PaymentResponse;
import com.flashsale.payment.service.PaymentQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final PaymentQueryService paymentQueryService;

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable("paymentId") String paymentId, Authentication caller) {
        boolean admin = caller.getAuthorities().stream().anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
        return ResponseEntity.ok(paymentQueryService.getPayment(paymentId, caller.getName(), admin));
    }
}
