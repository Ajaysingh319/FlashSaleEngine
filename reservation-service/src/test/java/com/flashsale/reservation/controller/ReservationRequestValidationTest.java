package com.flashsale.reservation.controller;

import com.flashsale.reservation.exception.PurchaseLimitExceededException;
import com.flashsale.reservation.security.JwtAuthenticationFilter;
import com.flashsale.reservation.security.ReservationSecurityConfig;
import com.flashsale.reservation.service.ReservationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Date;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bean Validation on POST /api/v1/reservations: invalid bodies are rejected before reaching the service. */
@WebMvcTest(controllers = ReservationController.class)
@Import({ReservationSecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "app.jwt.secret=" + ReservationRequestValidationTest.SECRET)
class ReservationRequestValidationTest {

    static final String SECRET = "Zmxhc2gtc2FsZS1lbmdpbmUtc2VjcmV0LWtleS0zMmIh";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservationService reservationService;

    private static String customerToken() {
        Instant now = Instant.now();
        return Jwts.builder().setSubject("user-1").claim("role", "CUSTOMER")
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), SignatureAlgorithm.HS256).compact();
    }

    private static MockHttpServletRequestBuilder asCustomer() {
        return post("/api/v1/reservations")
                .header("Authorization", "Bearer " + customerToken())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private ResultActions createReservation(String body) throws Exception {
        return mockMvc.perform(asCustomer().header("Idempotency-Key", "key-1").content(body));
    }

    /** 400 INVALID_REQUEST envelope whose message names the problem without leaking internals; service never called. */
    private void assertRejected(ResultActions result, String expectedMessagePart) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message", allOf(containsString(expectedMessagePart),
                        not(containsString("com.flashsale")), not(containsString("Exception")), not(containsString("jackson")))))
                .andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(reservationService);
    }

    // --- Quantity ---

    @Test
    void negativeQuantityIs400() throws Exception {
        assertRejected(createReservation("{\"eventId\":\"e\",\"ticketTypeId\":\"t\",\"quantity\":-1}"), "quantity");
    }

    @Test
    void zeroQuantityIs400() throws Exception {
        assertRejected(createReservation("{\"eventId\":\"e\",\"ticketTypeId\":\"t\",\"quantity\":0}"), "quantity");
    }

    @Test
    void missingQuantityIs400() throws Exception {
        assertRejected(createReservation("{\"eventId\":\"e\",\"ticketTypeId\":\"t\"}"), "quantity");
    }

    @Test
    void nonNumericQuantityIs400() throws Exception {
        assertRejected(createReservation("{\"eventId\":\"e\",\"ticketTypeId\":\"t\",\"quantity\":\"many\"}"),
                "Invalid value for field 'quantity'");
    }

    /** spring.jackson.deserialization.accept-float-as-int=false: fractions are rejected, not truncated to 2. */
    @Test
    void fractionalQuantityIs400() throws Exception {
        assertRejected(createReservation("{\"eventId\":\"e\",\"ticketTypeId\":\"t\",\"quantity\":2.5}"),
                "Invalid value for field 'quantity'");
    }

    @Test
    void fractionalQuantityAsStringIs400() throws Exception {
        assertRejected(createReservation("{\"eventId\":\"e\",\"ticketTypeId\":\"t\",\"quantity\":\"2.5\"}"),
                "Invalid value for field 'quantity'");
    }

    /** Any JSON number written in decimal form is rejected for an integer field, including whole values. */
    @Test
    void decimalNotationQuantityIs400() throws Exception {
        assertRejected(createReservation("{\"eventId\":\"e\",\"ticketTypeId\":\"t\",\"quantity\":2.0}"),
                "Invalid value for field 'quantity'");
    }

    @Test
    void integerQuantityTwoReachesServiceUnchanged() throws Exception {
        createReservation("{\"eventId\":\"e\",\"ticketTypeId\":\"t\",\"quantity\":2}")
                .andExpect(status().isOk());
        verify(reservationService).createReservation(eq("user-1"), eq("key-1"), argThat(request -> request.getQuantity() == 2));
    }

    /** The per-user limit is cumulative and owned by the service; it keeps its 409 contract rather than becoming a 400. */
    @Test
    void quantityAbovePurchaseLimitReachesServiceAndIs409() throws Exception {
        when(reservationService.createReservation(eq("user-1"), eq("key-1"), any()))
                .thenThrow(new PurchaseLimitExceededException(4));

        createReservation("{\"eventId\":\"e\",\"ticketTypeId\":\"t\",\"quantity\":5}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PURCHASE_LIMIT_EXCEEDED"));
        verify(reservationService).createReservation(eq("user-1"), eq("key-1"), argThat(request -> request.getQuantity() == 5));
    }

    // --- Other required fields ---

    @Test
    void blankEventIdIs400() throws Exception {
        assertRejected(createReservation("{\"eventId\":\" \",\"ticketTypeId\":\"t\",\"quantity\":1}"), "eventId");
    }

    @Test
    void missingTicketTypeIdIs400() throws Exception {
        assertRejected(createReservation("{\"eventId\":\"e\",\"quantity\":1}"), "ticketTypeId");
    }

    @Test
    void missingIdempotencyKeyIs400() throws Exception {
        assertRejected(mockMvc.perform(asCustomer().content("{\"eventId\":\"e\",\"ticketTypeId\":\"t\",\"quantity\":1}")),
                "Idempotency-Key");
    }

    // --- Malformed bodies ---

    @Test
    void malformedJsonIs400() throws Exception {
        assertRejected(createReservation("{\"eventId\":\"e\",\"quantity\":"), "not valid JSON");
    }

    @Test
    void missingBodyIs400() throws Exception {
        assertRejected(mockMvc.perform(asCustomer().header("Idempotency-Key", "key-1")), "missing");
    }

    // --- Valid requests are unchanged ---

    @Test
    void validQuantitiesReachService() throws Exception {
        for (int quantity = 1; quantity <= 4; quantity++) {
            createReservation("{\"eventId\":\"e\",\"ticketTypeId\":\"t\",\"quantity\":" + quantity + "}")
                    .andExpect(status().isOk());
        }
        for (int quantity = 1; quantity <= 4; quantity++) {
            int expected = quantity;
            verify(reservationService).createReservation(eq("user-1"), eq("key-1"),
                    argThat(request -> request.getQuantity() == expected));
        }
    }
}
