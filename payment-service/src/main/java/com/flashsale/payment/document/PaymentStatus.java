package com.flashsale.payment.document;

/**
 * PENDING until the provider answers; then SUCCESS, FAILED (declined) or TIMEOUT (PRD 6.8).
 * A SUCCESS payment becomes REFUNDED when Order cannot fulfil it.
 */
public enum PaymentStatus {
    PENDING, SUCCESS, FAILED, TIMEOUT, REFUNDED;

    public boolean isFailure() { return this == FAILED || this == TIMEOUT; }
}
