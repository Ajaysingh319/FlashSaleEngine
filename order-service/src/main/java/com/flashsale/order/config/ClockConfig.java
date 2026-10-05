package com.flashsale.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Injectable time source so time-based rules (order expiry) are testable. */
@Configuration
public class ClockConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
