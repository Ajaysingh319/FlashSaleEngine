package com.flashsale.payment.security;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Payments are read by their owner (CUSTOMER) or monitored by ADMIN; nothing else is exposed over HTTP. */
@Configuration
@RequiredArgsConstructor
public class PaymentSecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/*").hasAnyRole("CUSTOMER", "ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> response.sendError(HttpStatus.UNAUTHORIZED.value()))
                        .accessDeniedHandler((request, response, exception) -> response.sendError(HttpStatus.FORBIDDEN.value())))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
