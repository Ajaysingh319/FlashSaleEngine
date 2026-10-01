package com.flashsale.gateway.filter;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Filter to propagate or generate correlation IDs for distributed tracing.
 */
@Component
@Slf4j
public class CorrelationIdGatewayFilterFactory extends AbstractGatewayFilterFactory<CorrelationIdGatewayFilterFactory.Config> {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    public CorrelationIdGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);

            if (correlationId == null || correlationId.trim().isEmpty()) {
                correlationId = UUID.randomUUID().toString();
            }

            final String finalCorrelationId = correlationId;
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header(CORRELATION_ID_HEADER, finalCorrelationId)
                    .build();

            // Mutate request and also append to response
            exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, finalCorrelationId);

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    @Data
    public static class Config {
    }
}
