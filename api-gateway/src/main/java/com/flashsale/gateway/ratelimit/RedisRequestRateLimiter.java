package com.flashsale.gateway.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Fixed-window counter in Redis (TDD 52: {@code rate_limit:{userId}:{endpoint}}), shared by all Gateway instances.
 * Counting and starting the window happen in one script, so a key can never be left without an expiry.
 * If Redis is unavailable the request is allowed: inventory safety never depends on the rate limiter.
 */
@Component
@Slf4j
public class RedisRequestRateLimiter implements RequestRateLimiter {
    private static final RedisScript<Long> COUNT_IN_WINDOW = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final ReactiveStringRedisTemplate redis;

    public RedisRequestRateLimiter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Boolean> isAllowed(String key, int limit, Duration window) {
        return redis.execute(COUNT_IN_WINDOW, List.of(key), List.of(String.valueOf(window.toMillis())))
                .next()
                .map(count -> count <= limit)
                .defaultIfEmpty(true)
                .onErrorResume(error -> {
                    log.warn("Rate limiter unavailable, allowing request: {}", error.getMessage());
                    return Mono.just(true);
                });
    }
}
