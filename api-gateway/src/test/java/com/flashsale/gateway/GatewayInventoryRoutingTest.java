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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ticket availability is owned by Reservation Service (PRD 6.4, TDD 5): the public inventory routes must reach
 * Reservation, not Catalog. Catalog and Reservation are separate in-process stubs that record what they received.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayInventoryRoutingTest {

    private static final List<String> CATALOG_RECEIVED = new CopyOnWriteArrayList<>();
    private static final List<String> RESERVATION_RECEIVED = new CopyOnWriteArrayList<>();
    private static final HttpServer CATALOG = stub(CATALOG_RECEIVED);
    private static final HttpServer RESERVATION = stub(RESERVATION_RECEIVED);

    @Autowired
    private WebTestClient webTestClient;

    private static HttpServer stub(List<String> received) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                exchange.getRequestBody().readAllBytes();
                received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
                byte[] body = "[]".getBytes();
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
        String catalog = "http://127.0.0.1:" + CATALOG.getAddress().getPort();
        registry.add("CATALOG_SERVICE_URL", () -> catalog);
        registry.add("RESERVATION_SERVICE_URL", () -> "http://127.0.0.1:" + RESERVATION.getAddress().getPort());
        for (String other : List.of("AUTH", "ORDER", "PAYMENT")) {
            registry.add(other + "_SERVICE_URL", () -> catalog);
        }
    }

    @AfterAll
    static void stopStubs() {
        CATALOG.stop(0);
        RESERVATION.stop(0);
    }

    @BeforeEach
    void clear() {
        CATALOG_RECEIVED.clear();
        RESERVATION_RECEIVED.clear();
    }

    @Test
    void eventInventoryIsPublicAndServedByReservation() {
        webTestClient.get().uri("/api/v1/events/evt-1/inventory").exchange().expectStatus().isOk();

        assertThat(RESERVATION_RECEIVED).containsExactly("GET /api/v1/events/evt-1/inventory");
        assertThat(CATALOG_RECEIVED).isEmpty();
    }

    @Test
    void ticketTypeInventoryIsPublicAndServedByReservation() {
        webTestClient.get().uri("/api/v1/ticket-types/tt-1/inventory").exchange().expectStatus().isOk();

        assertThat(RESERVATION_RECEIVED).containsExactly("GET /api/v1/ticket-types/tt-1/inventory");
        assertThat(CATALOG_RECEIVED).isEmpty();
    }

    @Test
    void otherCatalogReadsStillReachCatalog() {
        webTestClient.get().uri("/api/v1/events/evt-1").exchange().expectStatus().isOk();
        webTestClient.get().uri("/api/v1/events/evt-1/ticket-types").exchange().expectStatus().isOk();
        webTestClient.get().uri("/api/v1/ticket-types/tt-1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer "
                        + AuthServiceTokens.accessToken(AuthServiceTokens.DEFAULT_SECRET, "user-1", "CUSTOMER", 60_000))
                .exchange().expectStatus().isOk();

        assertThat(CATALOG_RECEIVED).containsExactly(
                "GET /api/v1/events/evt-1", "GET /api/v1/events/evt-1/ticket-types", "GET /api/v1/ticket-types/tt-1");
        assertThat(RESERVATION_RECEIVED).isEmpty();
    }

    @Test
    void inventoryWritesAreNotPublic() {
        webTestClient.post().uri("/api/v1/ticket-types/tt-1/inventory").exchange().expectStatus().isUnauthorized();
        webTestClient.delete().uri("/api/v1/events/evt-1/inventory").exchange().expectStatus().isUnauthorized();

        assertThat(RESERVATION_RECEIVED).isEmpty();
        assertThat(CATALOG_RECEIVED).isEmpty();
    }
}
