package com.flashsale.payment.provider;

import com.flashsale.payment.document.Payment;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Simulated provider (PRD 6.8). Like a real provider's test cards, the payment method decides the outcome:
 * MOCK_CARD succeeds, MOCK_CARD_DECLINED fails, MOCK_CARD_TIMEOUT times out. Non-positive amounts are declined.
 */
@Component
public class MockPaymentProvider implements PaymentProvider {
    public static final String SUCCESS_METHOD = "MOCK_CARD";
    public static final String DECLINED_METHOD = "MOCK_CARD_DECLINED";
    public static final String TIMEOUT_METHOD = "MOCK_CARD_TIMEOUT";

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public PaymentOutcome charge(Payment payment) {
        if (payment.getAmount() == null || payment.getAmount().signum() <= 0) {
            return PaymentOutcome.declined("Amount must be positive");
        }
        return switch (payment.getPaymentMethod()) {
            case SUCCESS_METHOD -> PaymentOutcome.success("txn_" + UUID.randomUUID());
            case DECLINED_METHOD -> PaymentOutcome.declined("Card declined");
            case TIMEOUT_METHOD -> PaymentOutcome.timedOut();
            default -> PaymentOutcome.declined("Unsupported payment method");
        };
    }

    /** Simulated refund: the mock provider always accepts it. */
    @Override
    public void refund(Payment payment) {
    }
}
