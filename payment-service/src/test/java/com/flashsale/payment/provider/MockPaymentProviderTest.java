package com.flashsale.payment.provider;

import com.flashsale.payment.document.Payment;
import com.flashsale.payment.document.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MockPaymentProviderTest {

    private final MockPaymentProvider provider = new MockPaymentProvider();

    private static Payment payment(String method, String amount) {
        return Payment.start("pay-1", "order-1", "user-1", new BigDecimal(amount), method, Instant.now());
    }

    @Test
    void outcomeFollowsTheTestPaymentMethod() {
        assertEquals(PaymentStatus.SUCCESS, provider.charge(payment("MOCK_CARD", "10.00")).status());
        assertEquals(PaymentStatus.FAILED, provider.charge(payment("MOCK_CARD_DECLINED", "10.00")).status());
        assertEquals(PaymentStatus.TIMEOUT, provider.charge(payment("MOCK_CARD_TIMEOUT", "10.00")).status());
        assertEquals(PaymentStatus.FAILED, provider.charge(payment("UNKNOWN", "10.00")).status());
    }

    @Test
    void everySuccessfulChargeGetsItsOwnTransactionId() {
        String first = provider.charge(payment("MOCK_CARD", "10.00")).transactionId();
        String second = provider.charge(payment("MOCK_CARD", "10.00")).transactionId();

        assertTrue(first.startsWith("txn_"));
        assertNotEquals(first, second);
    }

    @Test
    void nonPositiveAmountIsDeclined() {
        PaymentOutcome outcome = provider.charge(payment("MOCK_CARD", "0"));

        assertEquals(PaymentStatus.FAILED, outcome.status());
        assertNull(outcome.transactionId());
    }

    @Test
    void timeoutAndDeclineAreFailures() {
        assertTrue(PaymentStatus.TIMEOUT.isFailure());
        assertTrue(PaymentStatus.FAILED.isFailure());
        assertFalse(PaymentStatus.SUCCESS.isFailure());
        assertEquals("MOCK", provider.name());
    }
}
