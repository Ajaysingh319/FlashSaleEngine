package com.flashsale.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the gateway with its real application.yml routes. Every downstream URL points at an in-process stub
 * that answers 200 and records what it received, so "allowed" means the request actually reached the service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayCatalogAdminRoutingTest {

    private static final String SECRET = AuthServiceTokens.DEFAULT_SECRET;
    private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();
    private static final HttpServer DOWNSTREAM = startDownstream();

    @Autowired
    private WebTestClient webTestClient;

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                exchange.getRequestBody().readAllBytes();
                String role = exchange.getRequestHeaders().getFirst("X-User-Role");
                RECEIVED.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " role=" + role);
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
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @BeforeEach
    void clearReceived() {
        RECEIVED.clear();
    }

    private static String token(String role) {
        return AuthServiceTokens.accessToken(SECRET, "user-" + role.toLowerCase(), role, 60_000);
    }

    private WebTestClient.ResponseSpec send(HttpMethod method, String path, String bearer) {
        WebTestClient.RequestBodySpec request = webTestClient.method(method).uri(path);
        if (bearer != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        }
        if (method != HttpMethod.DELETE) {
            request.contentType(MediaType.APPLICATION_JSON).bodyValue("{}");
        }
        return request.exchange();
    }

    /** Every management write on both catalog resources, for collection and item paths. */
    static Stream<Arguments> catalogWrites() {
        List<String> paths = List.of("/api/v1/events", "/api/v1/events/evt-1", "/api/v1/events/evt-1/cancel",
                "/api/v1/ticket-types", "/api/v1/ticket-types/tt-1");
        return paths.stream().flatMap(path -> Stream.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)
                .map(method -> Arguments.of(method, path)));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("catalogWrites")
    void anonymousCatalogWriteIsUnauthorized(HttpMethod method, String path) {
        send(method, path, null).expectStatus().isUnauthorized();
        assertThat(RECEIVED).isEmpty();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("catalogWrites")
    void customerCatalogWriteIsForbidden(HttpMethod method, String path) {
        send(method, path, token("CUSTOMER")).expectStatus().isForbidden();
        send(method, path, token("INTERNAL")).expectStatus().isForbidden();
        assertThat(RECEIVED).isEmpty();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("catalogWrites")
    void adminCatalogWriteReachesCatalog(HttpMethod method, String path) {
        send(method, path, token("ADMIN")).expectStatus().isOk();
        assertThat(RECEIVED).containsExactly(method.name() + " " + path + " role=ADMIN");
    }

    @Test
    void publicCatalogReadsNeedNoToken() {
        for (String path : List.of("/api/v1/events", "/api/v1/events/evt-1", "/api/v1/events/evt-1/ticket-types",
                "/api/v1/events/evt-1/inventory", "/api/v1/ticket-types/tt-1/inventory")) {
            send(HttpMethod.GET, path, null).expectStatus().isOk();
        }
        assertThat(RECEIVED).hasSize(5).allMatch(line -> line.startsWith("GET ") && line.endsWith("role=null"));
    }

    @Test
    void authenticatedCatalogReadIsUnchangedAndOpenToCustomers() {
        send(HttpMethod.GET, "/api/v1/ticket-types/tt-1", null).expectStatus().isUnauthorized();
        send(HttpMethod.GET, "/api/v1/ticket-types/tt-1", token("CUSTOMER")).expectStatus().isOk();
        assertThat(RECEIVED).containsExactly("GET /api/v1/ticket-types/tt-1 role=CUSTOMER");
    }

    @Test
    void unrelatedRoutesDoNotRequireAdmin() {
        send(HttpMethod.POST, "/api/v1/reservations", token("CUSTOMER")).expectStatus().isOk();
        send(HttpMethod.POST, "/api/v1/orders", token("CUSTOMER")).expectStatus().isOk();
        send(HttpMethod.POST, "/api/v1/auth/login", null).expectStatus().isOk();
        send(HttpMethod.POST, "/api/v1/auth/register", null).expectStatus().isOk();
        assertThat(RECEIVED).hasSize(4);
    }

    @Test
    void clientSuppliedRoleHeaderCannotGrantAdmin() {
        webTestClient.post().uri("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("CUSTOMER"))
                .header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden();
        assertThat(RECEIVED).isEmpty();
    }
}
