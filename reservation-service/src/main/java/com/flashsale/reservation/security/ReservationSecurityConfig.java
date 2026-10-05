package com.flashsale.reservation.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class ReservationSecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health").permitAll()
                        // Public, read-only ticket availability (PRD 16 Inventory API)
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/inventory", "/api/v1/ticket-types/*/inventory").permitAll()
                        // Service-to-service API (Order Service tokens)
                        .requestMatchers("/internal/**").hasRole("INTERNAL")
                        // Monitoring for administrators (PRD 6.14)
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // End-user API: Auth Service roles only, never service tokens
                        .requestMatchers("/api/v1/**").hasAnyRole("CUSTOMER", "ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> response.sendError(HttpStatus.UNAUTHORIZED.value()))
                        .accessDeniedHandler((request, response, exception) -> response.sendError(HttpStatus.FORBIDDEN.value())))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
