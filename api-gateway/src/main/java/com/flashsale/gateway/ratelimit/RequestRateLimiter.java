package com.flashsale.gateway.ratelimit;

import reactor.core.publisher.Mono;

import java.time.Duration;

/** Counts requests per key in fixed time windows (PRD 6.11, TDD 54). */
public interface RequestRateLimiter {

    /** Emits true while the requests counted for {@code key} in the current window are within {@code limit}. */
    Mono<Boolean> isAllowed(String key, int limit, Duration window);
}
