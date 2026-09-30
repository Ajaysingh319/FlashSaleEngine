package com.flashsale.order.document;

/**
 * Order status enum.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PAYMENT_FAILED,
    CANCELLED,
    EXPIRED
}