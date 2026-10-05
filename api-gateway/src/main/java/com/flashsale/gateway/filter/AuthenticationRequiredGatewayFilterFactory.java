package com.flashsale.gateway.filter;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.Data;
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

import java.text.ParseException;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Gateway filter to validate JWT for protected routes.
 * Extracts user information from valid token and forwards as headers.
 *
 * Tokens are issued by Auth Service: HS256, signed with the Base64-decoded JWT_SECRET,
 * "sub" = user ID and "role" = user role (e.g. CUSTOMER, ADMIN).
 */
@Component
@Slf4j
public class AuthenticationRequiredGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthenticationRequiredGatewayFilterFactory.Config> {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_ROLE_HEADER = "X-User-Role";
    /** Exchange attribute holding the verified user ID for later filters (attributes cannot be set by clients). */
    static final String AUTHENTICATED_USER_ID = "flashsale.authenticatedUserId";

    private static final int MIN_SECRET_BYTES = 32;

    private final MACVerifier verifier;

    public AuthenticationRequiredGatewayFilterFactory(@Value("${app.jwt.secret}") String jwtSecret) {
        super(Config.class);
        this.verifier = createVerifier(jwtSecret);
    }

    /** Decodes the secret exactly like Auth Service (standard Base64, at least 256 bits); fails fast otherwise. */
    private static MACVerifier createVerifier(String jwtSecret) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be configured");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(jwtSecret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be a Base64-encoded value", e);
        }
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must decode to at least 256 bits for HS256");
        }
        try {
            return new MACVerifier(keyBytes);
        } catch (JOSEException e) {
            throw new IllegalStateException("Invalid JWT secret", e);
        }
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
                if (!JWSAlgorithm.HS256.equals(signedJWT.getHeader().getAlgorithm())) {
                    return onError(exchange, "Unsupported Jwt algorithm", HttpStatus.UNAUTHORIZED);
                }
                if (!signedJWT.verify(verifier)) {
                    return onError(exchange, "Invalid Jwt signature", HttpStatus.UNAUTHORIZED);
                }
                JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
                // Validate expiration
                Date expiryTime = claims.getExpirationTime();
                if (expiryTime == null || !expiryTime.after(new Date())) {
                    return onError(exchange, "Expired Jwt token", HttpStatus.UNAUTHORIZED);
                }
                // Extract claims; access tokens always carry both (refresh tokens have no role)
                String userId = claims.getSubject();
                String role = claims.getStringClaim("role");
                if (isBlank(userId) || isBlank(role)) {
                    return onError(exchange, "Jwt token is not an access token", HttpStatus.UNAUTHORIZED);
                }
                // Authenticated but not permitted for this route
                if (config.getRequiredRole() != null && !config.getRequiredRole().equals(role)) {
                    return onError(exchange, "Role " + role + " is not permitted", HttpStatus.FORBIDDEN);
                }

                // Forward user info to backend services (overrides any client-supplied values)
                ServerHttpRequest mutatedRequest = request.mutate()
                        .header(USER_ID_HEADER, userId)
                        .header(USER_ROLE_HEADER, role)
                        .build();
                exchange.getAttributes().put(AUTHENTICATED_USER_ID, userId);

                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            } catch (ParseException e) {
                return onError(exchange, "Invalid Jwt token", HttpStatus.UNAUTHORIZED);
            } catch (Exception e) {
                return onError(exchange, "Jwt validation failed", HttpStatus.UNAUTHORIZED);
            }
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        log.debug("Rejecting request {}: {}", exchange.getRequest().getPath(), err);
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        return response.setComplete();
    }

    /** Route arguments, e.g. {@code - AuthenticationRequired=ADMIN}; no argument means any valid access token. */
    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("requiredRole");
    }

    @Data
    public static class Config {
        /** Exact role claim required (e.g. ADMIN); null allows any authenticated role. */
        private String requiredRole;
    }
}
