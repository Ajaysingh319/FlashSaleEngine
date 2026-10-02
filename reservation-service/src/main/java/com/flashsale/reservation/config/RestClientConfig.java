package com.flashsale.reservation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    /**
     * Client for synchronous calls to other services. Bounded timeouts make an unresponsive
     * dependency fail fast; URI variables are fully encoded so IDs cannot alter the request path.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder,
                              @Value("${catalog.service.connect-timeout:2s}") Duration connectTimeout,
                              @Value("${catalog.service.read-timeout:3s}") Duration readTimeout) {
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory();
        uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
        return builder
                .uriTemplateHandler(uriBuilderFactory)
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
