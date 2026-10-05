package com.flashsale.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin monitoring routes (PRD 16) are ADMIN-only and reach the service that owns the data: orders go to Order,
 * active reservations and statistics to Reservation. The profile route reaches Auth for any signed-in user.
 * Each downstream is an in-process stub that records the requests it received.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayAdminAndProfileRoutingTest {

    private static final List<String> AUTH_RECEIVED = new CopyOnWriteArrayList<>();
    private static final List<String> ORDER_RECEIVED = new CopyOnWriteArrayList<>();
    private static final List<String> RESERVATION_RECEIVED = new CopyOnWriteArrayList<>();
    private static final List<String> OTHER_RECEIVED = new CopyOnWriteArrayList<>();
    private static final Map<String, HttpServer> STUBS = Map.of(
            "AUTH", stub(AUTH_RECEIVED), "ORDER", stub(ORDER_RECEIVED),
            "RESERVATION", stub(RESERVATION_RECEIVED), "OTHER", stub(OTHER_RECEIVED));

    @Autowired
    private WebTestClient webTestClient;

    private static HttpServer stub(List<String> received) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                exchange.getRequestBody().readAllBytes();
                received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
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

    private static String url(String stub) {
        return "http://127.0.0.1:" + STUBS.get(stub).getAddress().getPort();
    }

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        registry.add("AUTH_SERVICE_URL", () -> url("AUTH"));
        registry.add("ORDER_SERVICE_URL", () -> url("ORDER"));
        registry.add("RESERVATION_SERVICE_URL", () -> url("RESERVATION"));
        registry.add("CATALOG_SERVICE_URL", () -> url("OTHER"));
        registry.add("PAYMENT_SERVICE_URL", () -> url("OTHER"));
    }

    @AfterAll
    static void stopStubs() {
        STUBS.values().forEach(server -> server.stop(0));
    }

    @BeforeEach
    void clear() {
        List.of(AUTH_RECEIVED, ORDER_RECEIVED, RESERVATION_RECEIVED, OTHER_RECEIVED).forEach(List::clear);
    }

    private static String bearer(String role) {
        return "Bearer " + AuthServiceTokens.accessToken(AuthServiceTokens.DEFAULT_SECRET, "user-1", role, 60_000);
    }

    private WebTestClient.ResponseSpec get(String path, String role) {
        WebTestClient.RequestHeadersSpec<?> request = webTestClient.get().uri(path);
        if (role != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, bearer(role));
        }
        return request.exchange();
    }

    @Test
    void adminOrderListGoesToOrderService() {
        get("/api/v1/admin/orders?status=CONFIRMED", "ADMIN").expectStatus().isOk();

        assertThat(ORDER_RECEIVED).containsExactly("GET /api/v1/admin/orders");
        assertThat(RESERVATION_RECEIVED).isEmpty();
    }

    @Test
    void activeReservationsAndStatisticsGoToReservationService() {
        get("/api/v1/admin/reservations/active", "ADMIN").expectStatus().isOk();
        get("/api/v1/admin/events/evt-1/statistics", "ADMIN").expectStatus().isOk();
        get("/api/v1/admin/statistics", "ADMIN").expectStatus().isOk();

        assertThat(RESERVATION_RECEIVED).containsExactly("GET /api/v1/admin/reservations/active",
                "GET /api/v1/admin/events/evt-1/statistics", "GET /api/v1/admin/statistics");
        assertThat(ORDER_RECEIVED).isEmpty();
        assertThat(OTHER_RECEIVED).isEmpty();
    }

    @Test
    void adminRoutesRejectCustomersAndAnonymousCallers() {
        for (String path : List.of("/api/v1/admin/orders", "/api/v1/admin/reservations/active",
                "/api/v1/admin/events/evt-1/statistics", "/api/v1/admin/statistics")) {
            get(path, "CUSTOMER").expectStatus().isForbidden();
            get(path, null).expectStatus().isUnauthorized();
        }
        assertThat(ORDER_RECEIVED).isEmpty();
        assertThat(RESERVATION_RECEIVED).isEmpty();
    }

    @Test
    void profileGoesToAuthServiceForAnySignedInUser() {
        get("/api/v1/users/me", "CUSTOMER").expectStatus().isOk();
        webTestClient.put().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN"))
                .bodyValue("{\"fullName\":\"Asha Rao\"}").exchange().expectStatus().isOk();

        assertThat(AUTH_RECEIVED).containsExactly("GET /api/v1/users/me", "PUT /api/v1/users/me");
    }

    @Test
    void profileRequiresAToken() {
        get("/api/v1/users/me", null).expectStatus().isUnauthorized();
        assertThat(AUTH_RECEIVED).isEmpty();
    }
}
