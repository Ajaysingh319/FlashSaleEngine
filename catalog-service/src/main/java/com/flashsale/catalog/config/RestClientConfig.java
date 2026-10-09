package com.flashsale.catalog.config;

import com.flashsale.catalog.observability.CorrelationId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    /** Client for Reservation Service: bounded timeouts, and URI variables fully encoded. */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder,
                                     @Value("${reservation.service.connect-timeout:2s}") Duration connectTimeout,
                                     @Value("${reservation.service.read-timeout:5s}") Duration readTimeout) {
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory();
        uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
        return builder
                .uriTemplateHandler(uriBuilderFactory)
                .additionalInterceptors(CorrelationId.propagateToHttpCalls())
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
    }
}
