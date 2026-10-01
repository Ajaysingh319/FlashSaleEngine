package com.flashsale.gateway.filter;

import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.crypto.MACVerifier;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import java.text.ParseException;
import java.util.Date;

/**
 * Gateway filter to validate JWT for protected routes.
 * Extracts user information from valid token and forwards as headers.
 */
@Component
@Slf4j
public class AuthenticationRequiredGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthenticationRequiredGatewayFilterFactory.Config> {

    @Value("${spring.credentials.secret}")
    private String jwtSecret;

    public AuthenticationRequiredGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Missing or malformed Jwt token", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            try {
                SignedJWT signedJWT = SignedJWT.parse(token);
                // Validate signature using HS256
                SecretKeySpec secretKeySpec = new SecretKeySpec(jwtSecret.getBytes(), "HS256");
                MACVerifier verifier = new MACVerifier(secretKeySpec);
                if (!signedJWT.verify(verifier)) {
                    return onError(exchange, "Invalid Jwt signature", HttpStatus.UNAUTHORIZED);
                }
                // Validate expiration
                Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
                if (expiryTime.before(new Date())) {
                    return onError(exchange, "Expired Jwt token", HttpStatus.UNAUTHORIZED);
                }
                // Extract claims
                String userId = signedJWT.getJWTClaimsSet().getSubject();
                String role = (String) signedJWT.getJWTClaimsSet().getClaim("role");

                // Forward user info to backend services
                ServerHttpRequest mutatedRequest = request.mutate()
                        .header("X-User-Id", userId)
                        .header("X-User-Role", role)
                        .build();

                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            } catch (ParseException e) {
                return onError(exchange, "Invalid Jwt token", HttpStatus.UNAUTHORIZED);
            } catch (Exception e) {
                return onError(exchange, "Jwt validation failed", HttpStatus.UNAUTHORIZED);
            }
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        return response.setComplete();
    }

    @Data
    public static class Config {
        // Placeholder for future configuration
    }
}