package com.flashsale.payment.provider;

import com.flashsale.payment.document.PaymentStatus;

/** A provider's answer for one charge; only a successful charge has a transaction ID. */
public record PaymentOutcome(PaymentStatus status, String transactionId, String failureReason) {

    public static PaymentOutcome success(String transactionId) {
        return new PaymentOutcome(PaymentStatus.SUCCESS, transactionId, null);
    }

    public static PaymentOutcome declined(String reason) {
        return new PaymentOutcome(PaymentStatus.FAILED, null, reason);
    }

    public static PaymentOutcome timedOut() {
        return new PaymentOutcome(PaymentStatus.TIMEOUT, null, "Payment provider did not respond in time");
    }
}
