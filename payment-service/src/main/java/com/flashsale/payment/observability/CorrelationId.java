package com.flashsale.payment.observability;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The trace ID of one request (TDD 68). The API Gateway creates it as X-Correlation-ID; every service puts it in
 * its logging context as "traceId" and passes it on to the services and Kafka events it triggers, so a purchase
 * can be followed from the Gateway to Payment Service.
 */
public final class CorrelationId {
    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "traceId";
    /** IDs the Gateway generates (UUIDs) and similar; anything else is replaced so a client cannot inject log text. */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private CorrelationId() {
    }

    /** The current request's trace ID, or null outside a request or event. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /** {@code candidate} if it is a well-formed ID, otherwise a new one. */
    public static String validOrNew(String candidate) {
        return candidate != null && VALID.matcher(candidate).matches() ? candidate : UUID.randomUUID().toString();
    }

    /** Makes the ID current for the calling thread until the returned handle is closed. */
    public static MDC.MDCCloseable open(String candidate) {
        return MDC.putCloseable(MDC_KEY, validOrNew(candidate));
    }
}
