package com.flashsale.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Injectable time source so timestamps are testable. */
@Configuration
public class ClockConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
