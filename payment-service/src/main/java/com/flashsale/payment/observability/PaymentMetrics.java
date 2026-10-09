package com.flashsale.payment.observability;

import com.flashsale.payment.document.PaymentStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Payment metrics of TDD 69: payment.success.count and payment.failure.count, tagged FAILED or TIMEOUT. */
@Component
public class PaymentMetrics {
    private final MeterRegistry registry;
    private final Counter successes;

    public PaymentMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.successes = registry.counter("payment.success.count");
    }

    /** Counts the provider's answer to one charge. */
    public void chargeCompleted(PaymentStatus status) {
        if (status == PaymentStatus.SUCCESS) {
            successes.increment();
        } else if (status.isFailure()) {
            registry.counter("payment.failure.count", "status", status.name()).increment();
        }
    }
}
