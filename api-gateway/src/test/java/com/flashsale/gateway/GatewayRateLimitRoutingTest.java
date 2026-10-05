package com.flashsale.gateway;

import com.flashsale.gateway.ratelimit.RequestRateLimiter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Login, reservation, order creation and payment initiation are rate limited (PRD 6.11, TDD 54); other routes
 * are not. The limiter is mocked to reject, so a limited route answers 429 without reaching the downstream stub.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.rate-limit.enabled=true")
class GatewayRateLimitRoutingTest {
    private static final Duration WINDOW = Duration.ofSeconds(60);
    private static final HttpServer DOWNSTREAM = stub();

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private RequestRateLimiter rateLimiter;

    private static HttpServer stub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                exchange.getRequestBody().readAllBytes();
                byte[] body = "{}".getBytes();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort();
        for (String service : List.of("AUTH", "CATALOG", "RESERVATION", "ORDER", "PAYMENT")) {
            registry.add(service + "_SERVICE_URL", () -> url);
        }
    }

    @AfterAll
    static void stopStub() {
        DOWNSTREAM.stop(0);
    }

    private static String bearer(String userId) {
        return "Bearer " + AuthServiceTokens.accessToken(AuthServiceTokens.DEFAULT_SECRET, userId, "CUSTOMER", 60_000);
    }

    private void expectLimited(String path, String token, String key, int limit) {
        reset(rateLimiter);
        when(rateLimiter.isAllowed(anyString(), anyInt(), any())).thenReturn(Mono.just(false));
        WebTestClient.RequestBodySpec request = webTestClient.post().uri(path);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, token);
        }
        request.exchange().expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(rateLimiter).isAllowed(eq(key), eq(limit), eq(WINDOW));
    }

    @Test
    void loginIsLimitedPerClientIp() {
        expectLimited("/api/v1/auth/login", null, "rate_limit:ip:127.0.0.1:login", 10);
    }

    @Test
    void reservationOrderAndPaymentAreLimitedPerUser() {
        expectLimited("/api/v1/reservations", bearer("user-1"), "rate_limit:user-1:reservation", 10);
        expectLimited("/api/v1/orders", bearer("user-1"), "rate_limit:user-1:order", 10);
        expectLimited("/api/v1/orders/order-1/payment", bearer("user-1"), "rate_limit:user-1:payment", 5);
    }

    @Test
    void limitedRouteStillRequiresAuthenticationFirst() {
        webTestClient.post().uri("/api/v1/reservations").exchange().expectStatus().isUnauthorized();
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void otherRoutesAreNotLimited() {
        webTestClient.get().uri("/api/v1/orders/me").header(HttpHeaders.AUTHORIZATION, bearer("user-1"))
                .exchange().expectStatus().isOk();
        webTestClient.post().uri("/api/v1/auth/register").exchange().expectStatus().isOk();
        verifyNoInteractions(rateLimiter);
    }
}
