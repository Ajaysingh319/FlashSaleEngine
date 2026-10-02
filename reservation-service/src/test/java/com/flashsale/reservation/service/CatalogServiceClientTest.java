package com.flashsale.reservation.service;

import com.flashsale.reservation.config.RestClientConfig;
import com.flashsale.reservation.dto.CatalogEventResponse;
import com.flashsale.reservation.exception.CatalogUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Exercises the client against a mocked HTTP layer using the production RestTemplate configuration. */
class CatalogServiceClientTest {

    private static final String BASE_URL = "http://catalog-service:8082";
    private static final String EVENT_URL = BASE_URL + "/api/v1/events/event-1";

    /** Shape of Catalog Service's EventResponse, including fields Reservation does not use. */
    private static final String EVENT_JSON = """
            {"id":"event-1","name":"Delhi Music Festival","description":"d","venue":"v","city":"Delhi",
             "startTime":"2026-12-01T18:00:00Z","endTime":"2026-12-02T00:00:00Z",
             "saleStartTime":"2026-11-01T10:00:00Z","saleEndTime":"2026-11-01T12:00:00Z",
             "status":"ON_SALE","createdAt":"2026-10-01T00:00:00Z","updatedAt":"2026-10-01T00:00:00Z"}
            """;

    private MockRestServiceServer server;
    private CatalogServiceClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestClientConfig()
                .restTemplate(new RestTemplateBuilder(), Duration.ofSeconds(2), Duration.ofSeconds(3));
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new CatalogServiceClient(restTemplate, BASE_URL);
    }

    @Test
    void readsEventFromCatalogEventDetailEndpoint() {
        server.expect(requestTo(EVENT_URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(EVENT_JSON, MediaType.APPLICATION_JSON));

        CatalogEventResponse event = client.findEvent("event-1").orElseThrow();

        assertEquals("event-1", event.getId());
        assertEquals("ON_SALE", event.getStatus());
        assertEquals(Instant.parse("2026-11-01T10:00:00Z"), event.getSaleStartTime());
        assertEquals(Instant.parse("2026-11-01T12:00:00Z"), event.getSaleEndTime());
        server.verify();
    }

    @Test
    void notFoundMeansEventDoesNotExist() {
        server.expect(requestTo(EVENT_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"success\":false,\"error\":{\"code\":\"EVENT_NOT_FOUND\",\"message\":\"Event not found\"}}"));

        assertEquals(Optional.empty(), client.findEvent("event-1"));
    }

    @Test
    void serverErrorFailsClosed() {
        server.expect(requestTo(EVENT_URL)).andRespond(withServerError());

        assertThrows(CatalogUnavailableException.class, () -> client.findEvent("event-1"));
    }

    @Test
    void otherClientErrorsFailClosed() {
        server.expect(requestTo(EVENT_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(CatalogUnavailableException.class, () -> client.findEvent("event-1"));
    }

    @Test
    void connectionFailureFailsClosed() {
        server.expect(requestTo(EVENT_URL)).andRespond(withException(new IOException("Connection refused")));

        assertThrows(CatalogUnavailableException.class, () -> client.findEvent("event-1"));
    }

    @Test
    void timeoutFailsClosed() {
        server.expect(requestTo(EVENT_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThrows(CatalogUnavailableException.class, () -> client.findEvent("event-1"));
    }

    @Test
    void emptyBodyFailsClosed() {
        server.expect(requestTo(EVENT_URL)).andRespond(withSuccess());

        assertThrows(CatalogUnavailableException.class, () -> client.findEvent("event-1"));
    }

    @Test
    void malformedJsonFailsClosed() {
        server.expect(requestTo(EVENT_URL)).andRespond(withSuccess("{not json", MediaType.APPLICATION_JSON));

        assertThrows(CatalogUnavailableException.class, () -> client.findEvent("event-1"));
    }

    @Test
    void responseForDifferentEventFailsClosed() {
        server.expect(requestTo(EVENT_URL))
                .andRespond(withSuccess(EVENT_JSON.replace("\"id\":\"event-1\"", "\"id\":\"event-2\""), MediaType.APPLICATION_JSON));

        assertThrows(CatalogUnavailableException.class, () -> client.findEvent("event-1"));
    }

    @Test
    void missingSaleWindowOrStatusFailsClosed() {
        server.expect(requestTo(EVENT_URL))
                .andRespond(withSuccess(EVENT_JSON.replace("\"saleStartTime\":\"2026-11-01T10:00:00Z\",", ""), MediaType.APPLICATION_JSON));
        assertThrows(CatalogUnavailableException.class, () -> client.findEvent("event-1"));

        server.reset();
        server.expect(requestTo(EVENT_URL))
                .andRespond(withSuccess(EVENT_JSON.replace("\"status\":\"ON_SALE\",", ""), MediaType.APPLICATION_JSON));
        assertThrows(CatalogUnavailableException.class, () -> client.findEvent("event-1"));
    }

    @Test
    void eventIdIsEncodedSoItCannotChangeTheRequestedPath() {
        server.expect(requestTo(BASE_URL + "/api/v1/events/..%2Fticket-types%2Ft-1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertEquals(Optional.empty(), client.findEvent("../ticket-types/t-1"));
        server.verify();
    }
}
