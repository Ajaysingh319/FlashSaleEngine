package com.flashsale.gateway.filter;

import com.flashsale.gateway.ratelimit.RequestRateLimiter;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;

/**
 * Limits high-value operations per caller (PRD 6.11, TDD 54), e.g. {@code - RateLimit=reservation, 10} allows
 * 10 requests per window. Callers are identified by the user ID verified by AuthenticationRequired (which must come
 * first on the route), or by client IP on public routes such as login. Exceeded limits return 429.
 */
@Component
public class RateLimitGatewayFilterFactory extends AbstractGatewayFilterFactory<RateLimitGatewayFilterFactory.Config> {
    static final String KEY_PREFIX = "rate_limit:";

    private final RequestRateLimiter rateLimiter;
    private final boolean enabled;
    private final Duration window;

    public RateLimitGatewayFilterFactory(RequestRateLimiter rateLimiter,
                                         @Value("${app.rate-limit.enabled:true}") boolean enabled,
                                         @Value("${app.rate-limit.window:60s}") Duration window) {
        super(Config.class);
        this.rateLimiter = rateLimiter;
        this.enabled = enabled;
        this.window = window;
    }

    @Override
    public GatewayFilter apply(Config config) {
        config.validate();
        return (exchange, chain) -> {
            if (!enabled) {
                return chain.filter(exchange);
            }
            String key = KEY_PREFIX + caller(exchange) + ":" + config.getName();
            return rateLimiter.isAllowed(key, config.getLimit(), window)
                    .flatMap(allowed -> allowed ? chain.filter(exchange) : reject(exchange));
        };
    }

    /** Trusts only the user ID set by AuthenticationRequired (never a client header); otherwise the client IP. */
    private static String caller(ServerWebExchange exchange) {
        String userId = exchange.getAttribute(AuthenticationRequiredGatewayFilterFactory.AUTHENTICATED_USER_ID);
        if (userId != null) {
            return userId;
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return "ip:" + (remote == null ? "unknown" : remote.getAddress().getHostAddress());
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(window.toSeconds()));
        return exchange.getResponse().setComplete();
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("name", "limit");
    }

    @Data
    public static class Config {
        /** Operation name used in the Redis key, e.g. "reservation". */
        private String name;
        /** Requests allowed per caller per window. */
        private int limit;

        void validate() {
            if (name == null || name.isBlank() || limit < 1) {
                throw new IllegalArgumentException("RateLimit needs a name and a positive limit");
            }
        }
    }
}
