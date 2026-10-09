package com.flashsale.order.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Order metrics of TDD 69: order.created.count and order.confirmed.count. */
@Component
public class OrderMetrics {
    private final Counter created;
    private final Counter confirmed;

    public OrderMetrics(MeterRegistry registry) {
        this.created = registry.counter("order.created.count");
        this.confirmed = registry.counter("order.confirmed.count");
    }

    public void orderCreated() {
        created.increment();
    }

    public void orderConfirmed() {
        confirmed.increment();
    }
}
