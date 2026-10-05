package com.flashsale.gateway.filter;

import com.flashsale.gateway.ratelimit.RequestRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RateLimitGatewayFilterFactoryTest {
    private static final Duration WINDOW = Duration.ofSeconds(60);

    private final RequestRateLimiter rateLimiter = mock(RequestRateLimiter.class);
    private final AtomicBoolean forwarded = new AtomicBoolean();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(true);
        return Mono.empty();
    };

    private GatewayFilter filter(boolean enabled) {
        RateLimitGatewayFilterFactory.Config config = new RateLimitGatewayFilterFactory.Config();
        config.setName("reservation");
        config.setLimit(10);
        return new RateLimitGatewayFilterFactory(rateLimiter, enabled, WINDOW).apply(config);
    }

    private static MockServerWebExchange authenticated(String userId) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/reservations"));
        exchange.getAttributes().put(AuthenticationRequiredGatewayFilterFactory.AUTHENTICATED_USER_ID, userId);
        return exchange;
    }

    @Test
    void requestWithinTheLimitIsForwardedAndCountedPerUserAndOperation() {
        when(rateLimiter.isAllowed("rate_limit:user-1:reservation", 10, WINDOW)).thenReturn(Mono.just(true));

        filter(true).filter(authenticated("user-1"), chain).block();

        assertThat(forwarded).isTrue();
    }

    @Test
    void requestOverTheLimitIsRejectedWith429AndRetryAfter() {
        when(rateLimiter.isAllowed(anyString(), anyInt(), any())).thenReturn(Mono.just(false));
        MockServerWebExchange exchange = authenticated("user-1");

        filter(true).filter(exchange, chain).block();

        assertThat(forwarded).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
    }

    @Test
    void unauthenticatedCallerIsCountedByClientIpNeverByAClientSuppliedUserHeader() {
        when(rateLimiter.isAllowed(anyString(), anyInt(), any())).thenReturn(Mono.just(true));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login")
                .header("X-User-Id", "spoofed").remoteAddress(new InetSocketAddress("203.0.113.7", 5000)));

        filter(true).filter(exchange, chain).block();

        verify(rateLimiter).isAllowed(eq("rate_limit:ip:203.0.113.7:reservation"), eq(10), eq(WINDOW));
    }

    @Test
    void disabledRateLimitingForwardsWithoutCounting() {
        filter(false).filter(authenticated("user-1"), chain).block();

        assertThat(forwarded).isTrue();
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void routeWithoutANameOrAPositiveLimitIsRejectedAtStartup() {
        RateLimitGatewayFilterFactory factory = new RateLimitGatewayFilterFactory(rateLimiter, true, WINDOW);
        RateLimitGatewayFilterFactory.Config config = new RateLimitGatewayFilterFactory.Config();
        config.setName("login");

        assertThatThrownBy(() -> factory.apply(config)).isInstanceOf(IllegalArgumentException.class);
    }
}
