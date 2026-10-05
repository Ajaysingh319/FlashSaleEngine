package com.flashsale.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Body of POST /api/v1/orders/{orderId}/payment (TDD 48). */
public record PaymentInitiationRequest(
        @NotBlank
        @Pattern(regexp = "MOCK_CARD|MOCK_CARD_DECLINED|MOCK_CARD_TIMEOUT",
                message = "must be MOCK_CARD, MOCK_CARD_DECLINED or MOCK_CARD_TIMEOUT")
        String paymentMethod) { }
