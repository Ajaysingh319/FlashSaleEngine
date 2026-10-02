package com.flashsale.gateway.filter;

import com.flashsale.gateway.AuthServiceTokens;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticationRequiredGatewayFilterFactoryTest {

    private static final String SECRET = AuthServiceTokens.DEFAULT_SECRET;
    private static final String OTHER_SECRET = Base64.getEncoder().encodeToString(
            "another-flash-sale-secret-key-0123456789".getBytes());

    private final GatewayFilter filter = new AuthenticationRequiredGatewayFilterFactory(SECRET)
            .apply(new AuthenticationRequiredGatewayFilterFactory.Config());

    @Test
    void acceptsAuthServiceAccessTokenAndForwardsUserIdAndRole() {
        String token = AuthServiceTokens.accessToken(SECRET, "665f1c2ab3e4d5f6a7b8c9d0", "CUSTOMER", 60_000);

        Result result = run(token);

        assertThat(result.forwarded).isNotNull();
        assertThat(result.forwarded.getHeaders().getFirst("X-User-Id")).isEqualTo("665f1c2ab3e4d5f6a7b8c9d0");
        assertThat(result.forwarded.getHeaders().getFirst("X-User-Role")).isEqualTo("CUSTOMER");
        assertThat(result.forwarded.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + token);
    }

    @Test
    void forwardsAdminRoleUnchanged() {
        Result result = run(AuthServiceTokens.accessToken(SECRET, "admin-id", "ADMIN", 60_000));

        assertThat(result.forwarded.getHeaders().getFirst("X-User-Role")).isEqualTo("ADMIN");
    }

    @Test
    void overridesClientSuppliedIdentityHeaders() {
        String token = AuthServiceTokens.accessToken(SECRET, "real-user", "CUSTOMER", 60_000);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Id", "spoofed-user")
                .header("X-User-Role", "ADMIN")
                .build();

        Result result = run(request);

        assertThat(result.forwarded.getHeaders().get("X-User-Id")).containsExactly("real-user");
        assertThat(result.forwarded.getHeaders().get("X-User-Role")).containsExactly("CUSTOMER");
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        Result result = run(AuthServiceTokens.accessToken(OTHER_SECRET, "user-1", "CUSTOMER", 60_000));

        assertRejected(result);
    }

    @Test
    void rejectsTamperedPayload() {
        String token = AuthServiceTokens.accessToken(SECRET, "user-1", "CUSTOMER", 60_000);
        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"role\":\"ADMIN\",\"sub\":\"user-1\",\"exp\":9999999999}".getBytes());

        Result result = run(parts[0] + "." + forgedPayload + "." + parts[2]);

        assertRejected(result);
    }

    @Test
    void rejectsExpiredToken() {
        Result result = run(AuthServiceTokens.accessToken(SECRET, "user-1", "CUSTOMER", -1_000));

        assertRejected(result);
    }

    @Test
    void rejectsTokenWithoutExpiration() {
        String token = Jwts.builder().setClaims(Map.of("role", "CUSTOMER")).setSubject("user-1")
                .signWith(AuthServiceTokens.key(SECRET), SignatureAlgorithm.HS256).compact();

        assertRejected(run(token));
    }

    @Test
    void rejectsRefreshTokenBecauseItHasNoRole() {
        String refreshToken = AuthServiceTokens.refreshToken(SECRET, "user-1", 60_000);

        assertRejected(run(refreshToken));
    }

    @Test
    void rejectsAlgorithmOtherThanHs256() {
        String longSecret = Base64.getEncoder().encodeToString(new byte[64]);
        GatewayFilter longKeyFilter = new AuthenticationRequiredGatewayFilterFactory(longSecret)
                .apply(new AuthenticationRequiredGatewayFilterFactory.Config());
        String hs512 = Jwts.builder().setClaims(Map.of("role", "CUSTOMER")).setSubject("user-1")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(AuthServiceTokens.key(longSecret), SignatureAlgorithm.HS512).compact();

        assertRejected(run(longKeyFilter, request(hs512)));
    }

    @Test
    void rejectsUnsignedToken() {
        String unsigned = Jwts.builder().setClaims(Map.of("role", "CUSTOMER")).setSubject("user-1")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000)).compact();

        assertRejected(run(unsigned));
    }

    @Test
    void rejectsMissingOrMalformedAuthorizationHeader() {
        assertRejected(run(MockServerHttpRequest.get("/api/v1/orders/me").build()));
        assertRejected(run(MockServerHttpRequest.get("/api/v1/orders/me")
                .header(HttpHeaders.AUTHORIZATION, "Basic abc").build()));
        assertRejected(run("not-a-jwt"));
    }

    @Test
    void failsFastOnSecretThatIsNotBase64OrTooShort() {
        assertThatThrownBy(() -> new AuthenticationRequiredGatewayFilterFactory("not base64 !!"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AuthenticationRequiredGatewayFilterFactory(
                Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AuthenticationRequiredGatewayFilterFactory(" "))
                .isInstanceOf(IllegalStateException.class);
    }

    private static MockServerHttpRequest request(String token) {
        return MockServerHttpRequest.get("/api/v1/orders/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
    }

    private Result run(String token) {
        return run(request(token));
    }

    private Result run(MockServerHttpRequest request) {
        return run(filter, request);
    }

    private static Result run(GatewayFilter gatewayFilter, MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        gatewayFilter.filter(exchange, ex -> {
            forwarded.set(ex.getRequest());
            return Mono.empty();
        }).block();
        return new Result(forwarded.get(), (HttpStatus) exchange.getResponse().getStatusCode());
    }

    private static void assertRejected(Result result) {
        assertThat(result.forwarded).isNull();
        assertThat(result.status).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private record Result(ServerHttpRequest forwarded, HttpStatus status) {
    }
}
