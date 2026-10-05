package com.flashsale.order.dto;

/** Returned immediately while the payment continues asynchronously (PRD 6.8, TDD 48). */
public record PaymentInitiationResponse(String paymentId, String orderId, String status) { }
