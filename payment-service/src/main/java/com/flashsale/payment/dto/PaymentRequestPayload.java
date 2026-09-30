package com.flashsale.payment.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;

/** Accepts the Order snapshot's totalAmount without coupling to Order's DTO. */
public record PaymentRequestPayload(
        String orderId,
        String userId,
        @JsonAlias({"totalAmount"}) BigDecimal amount) { }
