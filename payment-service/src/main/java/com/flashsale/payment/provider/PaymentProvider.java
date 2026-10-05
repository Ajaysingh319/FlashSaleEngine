package com.flashsale.payment.provider;

import com.flashsale.payment.document.Payment;

/** Charges a payment with an external provider; the MVP uses {@link MockPaymentProvider} (PRD 6.8). */
public interface PaymentProvider {

    /** Provider name stored on the payment (TDD 18). */
    String name();

    PaymentOutcome charge(Payment payment);

    /** Returns the money of a successful charge. */
    void refund(Payment payment);
}
