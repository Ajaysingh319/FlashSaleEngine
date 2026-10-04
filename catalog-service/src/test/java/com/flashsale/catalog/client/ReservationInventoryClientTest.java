package com.flashsale.catalog.client;

import com.flashsale.catalog.config.RestClientConfig;
import com.flashsale.catalog.exception.InventoryProvisioningException;
import com.flashsale.catalog.exception.TicketTypeConflictException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Consumer side of the Catalog -> Reservation inventory setup contract. The request body asserted here is the
 * one Reservation's InventoryInitializationContractTest accepts.
 */
class ReservationInventoryClientTest {

    private static final String SECRET = "Zmxhc2gtc2FsZS1lbmdpbmUtc2VjcmV0LWtleS0zMmIh";
    private static final String BASE_URL = "http://reservation-service:8083";
    private static final String SETUP_URL = BASE_URL + "/internal/v1/inventory";

    /** Must match InventoryInitializationContractTest.CATALOG_SETUP_BODY in reservation-service. */
    private static final String EXPECTED_BODY = """
            {"eventId":"event-1","ticketTypeId":"tt-1","totalQuantity":100,
             "availableQuantity":100,"reservedQuantity":0,"soldQuantity":0}
            """;

    private static final String INVENTORY_RESPONSE = """
            {"id":"tt-1","eventId":"event-1","ticketTypeId":"tt-1","totalQuantity":100,
             "availableQuantity":100,"reservedQuantity":0,"soldQuantity":0,"updatedAt":"2026-10-03T10:00:00Z"}
            """;

    private MockRestServiceServer server;
    private ReservationInventoryClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestClientConfig()
                .restTemplate(new RestTemplateBuilder(), Duration.ofSeconds(2), Duration.ofSeconds(5));
        server = MockRestServiceServer.bindTo(restTemplate).build();
        InternalJwtTokenProvider tokenProvider = new InternalJwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "secret", SECRET);
        tokenProvider.initialize();
        client = new ReservationInventoryClient(restTemplate, tokenProvider, BASE_URL);
    }

    @Test
    void postsInitialStockToReservationWithInternalServiceToken() {
        AtomicReference<String> authorization = new AtomicReference<>();
        server.expect(requestTo(SETUP_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(EXPECTED_BODY, true))
                .andExpect(request -> authorization.set(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)))
                .andRespond(withSuccess(INVENTORY_RESPONSE, MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> client.initializeInventory("event-1", "tt-1", 100));
        server.verify();

        assertTrue(authorization.get().startsWith("Bearer "));
        Claims claims = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET))).build()
                .parseClaimsJws(authorization.get().substring(7)).getBody();
        assertEquals("catalog-service", claims.getSubject());
        assertEquals("INTERNAL", claims.get("role", String.class));
        assertNotNull(claims.getExpiration());
    }

    @Test
    void replayOfExistingInventoryWithSalesIsAccepted() {
        server.expect(requestTo(SETUP_URL)).andRespond(withSuccess(
                INVENTORY_RESPONSE.replace("\"availableQuantity\":100", "\"availableQuantity\":40")
                        .replace("\"soldQuantity\":0", "\"soldQuantity\":60"), MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> client.initializeInventory("event-1", "tt-1", 100));
    }

    @Test
    void conflictFromReservationIsATicketTypeConflict() {
        server.expect(requestTo(SETUP_URL)).andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"success\":false,\"error\":{\"code\":\"INVENTORY_ALREADY_INITIALIZED\",\"message\":\"x\"}}"));

        assertThrows(TicketTypeConflictException.class, () -> client.initializeInventory("event-1", "tt-1", 100));
    }

    @Test
    void serverErrorIsAProvisioningFailure() {
        server.expect(requestTo(SETUP_URL)).andRespond(withServerError());

        assertThrows(InventoryProvisioningException.class, () -> client.initializeInventory("event-1", "tt-1", 100));
    }

    @Test
    void connectionFailureIsAProvisioningFailure() {
        server.expect(requestTo(SETUP_URL)).andRespond(withException(new IOException("Connection refused")));

        assertThrows(InventoryProvisioningException.class, () -> client.initializeInventory("event-1", "tt-1", 100));
    }

    @Test
    void rejectedServiceTokenIsAProvisioningFailure() {
        server.expect(requestTo(SETUP_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThrows(InventoryProvisioningException.class, () -> client.initializeInventory("event-1", "tt-1", 100));
    }

    @Test
    void responseForDifferentInventoryIsAProvisioningFailure() {
        server.expect(requestTo(SETUP_URL)).andRespond(withSuccess(
                INVENTORY_RESPONSE.replace("\"totalQuantity\":100", "\"totalQuantity\":50"), MediaType.APPLICATION_JSON));

        assertThrows(InventoryProvisioningException.class, () -> client.initializeInventory("event-1", "tt-1", 100));
    }

    @Test
    void emptyResponseIsAProvisioningFailure() {
        server.expect(requestTo(SETUP_URL)).andRespond(withSuccess());

        assertThrows(InventoryProvisioningException.class, () -> client.initializeInventory("event-1", "tt-1", 100));
    }
}
