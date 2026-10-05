package com.flashsale.order.document;

/**
 * Order's view of its payment: PENDING (not started) -> PROCESSING (requested) -> SUCCEEDED or FAILED.
 * REFUND_REQUESTED: the charge succeeded but the order could not be fulfilled, so the payment is being refunded;
 * REFUNDED once Payment Service confirms the money was returned.
 */
public enum PaymentStatus { PENDING, PROCESSING, SUCCEEDED, FAILED, REFUND_REQUESTED, REFUNDED }
