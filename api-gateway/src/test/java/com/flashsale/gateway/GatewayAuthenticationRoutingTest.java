package com.flashsale.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the gateway with its real application.yml routes. Downstream services are not running
 * (all route URIs point to a closed port), so a request that passes authentication fails with a
 * 5xx from the proxy, while a rejected request gets 401 from the gateway itself.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "AUTH_SERVICE_URL=http://localhost:1",
        "CATALOG_SERVICE_URL=http://localhost:1",
        "RESERVATION_SERVICE_URL=http://localhost:1",
        "ORDER_SERVICE_URL=http://localhost:1",
        "PAYMENT_SERVICE_URL=http://localhost:1"
})
class GatewayAuthenticationRoutingTest {

    private static final String SECRET = AuthServiceTokens.DEFAULT_SECRET;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void protectedRouteWithoutTokenIsRejected() {
        webTestClient.get().uri("/api/v1/orders/me").exchange()
                .expectStatus().isUnauthorized();
        webTestClient.get().uri("/api/v1/reservations/me").exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRouteWithInvalidOrExpiredTokenIsRejected() {
        String expired = AuthServiceTokens.accessToken(SECRET, "user-1", "CUSTOMER", -1_000);
        webTestClient.get().uri("/api/v1/orders/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)
                .exchange()
                .expectStatus().isUnauthorized();
        webTestClient.get().uri("/api/v1/orders/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired + "x")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRouteWithAuthServiceTokenPassesAuthentication() {
        String token = AuthServiceTokens.accessToken(SECRET, "user-1", "CUSTOMER", 60_000);

        HttpStatus status = (HttpStatus) webTestClient.get().uri("/api/v1/orders/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .returnResult(Void.class).getStatus();

        assertThat(status.is5xxServerError()).as("proxied to (unavailable) downstream, got %s", status).isTrue();
    }

    @Test
    void publicAuthRouteDoesNotRequireToken() {
        HttpStatus status = (HttpStatus) webTestClient.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"a@b.c\",\"password\":\"x\"}")
                .exchange()
                .returnResult(Void.class).getStatus();

        assertThat(status.is5xxServerError()).as("proxied to (unavailable) downstream, got %s", status).isTrue();
    }
}
