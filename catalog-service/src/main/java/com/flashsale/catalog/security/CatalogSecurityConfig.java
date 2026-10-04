package com.flashsale.catalog.security;

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

/**
 * Catalog reads are public (customers browse; Order and Reservation read events and ticket types without a token).
 * Event and ticket-type management requires ADMIN (PRD 6.1), enforced here as well as at the API Gateway so that
 * calling Catalog directly does not bypass it. Anything not listed is denied to non-admins by default.
 */
@Configuration
@RequiredArgsConstructor
public class CatalogSecurityConfig {
    private static final String[] MANAGED_RESOURCES = {"/api/v1/events/**", "/api/v1/ticket-types/**"};

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/v1/**").permitAll()
                        .requestMatchers(HttpMethod.POST, MANAGED_RESOURCES).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, MANAGED_RESOURCES).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, MANAGED_RESOURCES).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, MANAGED_RESOURCES).hasRole("ADMIN")
                        .anyRequest().hasRole("ADMIN"))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> response.sendError(HttpStatus.UNAUTHORIZED.value()))
                        .accessDeniedHandler((request, response, exception) -> response.sendError(HttpStatus.FORBIDDEN.value())))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
