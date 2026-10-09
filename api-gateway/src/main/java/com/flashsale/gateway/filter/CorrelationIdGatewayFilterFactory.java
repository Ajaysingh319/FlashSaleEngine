package com.flashsale.gateway.filter;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Starts the trace (TDD 68): every request gets an X-Correlation-ID that the services log as "traceId" and pass on
 * to each other and to Kafka events. Returned to the client too, so a user-reported problem can be found in the logs.
 */
@Component
@Slf4j
public class CorrelationIdGatewayFilterFactory extends AbstractGatewayFilterFactory<CorrelationIdGatewayFilterFactory.Config> {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    /** Same rule as the services' CorrelationId: UUID-like IDs only. */
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    public CorrelationIdGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);

            // Keep a well-formed client ID; replace anything else so it cannot inject text into service logs.
            if (correlationId == null || !VALID_ID.matcher(correlationId).matches()) {
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
