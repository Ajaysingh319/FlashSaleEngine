package com.flashsale.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Spring Security is on the classpath (oauth2-resource-server provides the Nimbus JOSE library),
 * but JWT authentication is enforced per route by {@code AuthenticationRequiredGatewayFilterFactory}
 * as configured in application.yml. This chain disables Spring Security's default session-based
 * mechanisms (basic/form login, CSRF) so that public routes stay public and protected routes are
 * guarded only by the route filter. Without it the default chain fails to start.
 */
@Configuration
public class GatewaySecurityConfig {

    @Bean
    SecurityWebFilterChain gatewaySecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }
}
