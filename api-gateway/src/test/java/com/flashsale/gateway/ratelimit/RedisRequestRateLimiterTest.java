package com.flashsale.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class RedisRequestRateLimiterTest {
    private static final Duration WINDOW = Duration.ofSeconds(60);

    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    private final RedisRequestRateLimiter rateLimiter = new RedisRequestRateLimiter(redis);

    private void countIs(long count) {
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.just(count));
    }

    @Test
    void allowsRequestsUpToTheLimitInTheWindow() {
        countIs(10);

        assertThat(rateLimiter.isAllowed("rate_limit:user-1:reservation", 10, WINDOW).block()).isTrue();
        verify(redis).execute(any(RedisScript.class), eq(List.of("rate_limit:user-1:reservation")), eq(List.of("60000")));
    }

    @Test
    void rejectsTheRequestThatExceedsTheLimit() {
        countIs(11);

        assertThat(rateLimiter.isAllowed("rate_limit:user-1:reservation", 10, WINDOW).block()).isFalse();
    }

    @Test
    void redisOutageAllowsTheRequest() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new RedisConnectionFailureException("down")));

        assertThat(rateLimiter.isAllowed("rate_limit:user-1:reservation", 10, WINDOW).block()).isTrue();
    }
}
