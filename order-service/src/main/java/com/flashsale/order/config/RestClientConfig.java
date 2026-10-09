package com.flashsale.order.config;

import com.flashsale.order.observability.CorrelationId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Configuration
public class RestClientConfig {
    /** Client for Reservation Service; every call carries the current trace ID. */
    @Bean
    RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of(CorrelationId.propagateToHttpCalls()));
        return restTemplate;
    }
}
