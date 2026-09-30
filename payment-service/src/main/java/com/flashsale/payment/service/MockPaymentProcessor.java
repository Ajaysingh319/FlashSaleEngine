package com.flashsale.payment.service;

import com.flashsale.payment.document.Payment;
import com.flashsale.payment.document.PaymentStatus;
import org.springframework.stereotype.Component;

/** Deterministic MVP processor: a strictly positive amount succeeds; all other amounts fail. */
@Component
public class MockPaymentProcessor {
    public PaymentStatus process(Payment payment) {
        return payment.getAmount() != null && payment.getAmount().signum() > 0
                ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;
    }
}
