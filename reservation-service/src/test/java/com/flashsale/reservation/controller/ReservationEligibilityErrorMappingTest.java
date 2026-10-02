package com.flashsale.reservation.controller;

import com.flashsale.reservation.exception.CatalogUnavailableException;
import com.flashsale.reservation.exception.EventCancelledException;
import com.flashsale.reservation.exception.EventNotFoundException;
import com.flashsale.reservation.exception.EventNotOnSaleException;
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

import java.time.Instant;
import java.util.Date;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP status and error-envelope mapping for event eligibility failures on reservation creation. */
@WebMvcTest(controllers = ReservationController.class)
@Import({ReservationSecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "app.jwt.secret=" + ReservationEligibilityErrorMappingTest.SECRET)
class ReservationEligibilityErrorMappingTest {

    static final String SECRET = "Zmxhc2gtc2FsZS1lbmdpbmUtc2VjcmV0LWtleS0zMmIh";
    private static final String BODY = "{\"eventId\":\"event-1\",\"ticketTypeId\":\"ticket-1\",\"quantity\":1}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservationService reservationService;

    private static String token(String subject, String role) {
        Instant now = Instant.now();
        return Jwts.builder().setSubject(subject).claim("role", role)
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), SignatureAlgorithm.HS256).compact();
    }

    private ResultActions createReservationAsCustomer() throws Exception {
        return mockMvc.perform(post("/api/v1/reservations")
                .header("Authorization", "Bearer " + token("user-1", "CUSTOMER"))
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON).content(BODY));
    }

    private void serviceThrows(RuntimeException exception) {
        when(reservationService.createReservation(eq("user-1"), eq("key-1"), any())).thenThrow(exception);
    }

    @Test
    void unknownEventIs404EventNotFound() throws Exception {
        serviceThrows(new EventNotFoundException("event-1"));

        createReservationAsCustomer()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void cancelledEventIs409EventCancelled() throws Exception {
        serviceThrows(new EventCancelledException("event-1"));

        createReservationAsCustomer()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EVENT_CANCELLED"));
    }

    @Test
    void saleNotStartedIs409EventNotOnSale() throws Exception {
        serviceThrows(new EventNotOnSaleException("Ticket sales for event event-1 have not started"));

        createReservationAsCustomer()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EVENT_NOT_ON_SALE"))
                .andExpect(jsonPath("$.error.message").value("Ticket sales for event event-1 have not started"));
    }

    @Test
    void saleEndedIs409EventNotOnSale() throws Exception {
        serviceThrows(new EventNotOnSaleException("Ticket sales for event event-1 have ended"));

        createReservationAsCustomer()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EVENT_NOT_ON_SALE"))
                .andExpect(jsonPath("$.error.message").value("Ticket sales for event event-1 have ended"));
    }

    @Test
    void catalogUnavailableIs503WithoutLeakingInternalDetails() throws Exception {
        serviceThrows(new CatalogUnavailableException("Could not verify event event-1 with Catalog Service",
                new RuntimeException("I/O error on GET request for http://catalog-service:8082/...")));

        createReservationAsCustomer()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message", not(containsString("catalog-service:8082"))));
    }

    @Test
    void internalTokenIsStillForbiddenBeforeEligibilityIsChecked() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + token("order-service", "INTERNAL"))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reservationService);
    }

    @Test
    void anonymousRequestIsStillUnauthorizedBeforeEligibilityIsChecked() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reservationService);
    }
}
