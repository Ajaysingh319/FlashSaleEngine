package com.flashsale.payment.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** payment.requested payload from Order's event snapshot; paymentId is assigned by Order at initiation. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentRequestPayload(
        String orderId,
        String userId,
        @JsonAlias({"totalAmount"}) BigDecimal amount,
        String paymentId,
        String paymentMethod) { }
