package com.flashsale.reservation.controller;

import com.flashsale.reservation.dto.InventoryResponse;
import com.flashsale.reservation.exception.InventoryNotFoundException;
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

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public, read-only ticket availability; everything else under /api/v1 stays protected. */
@WebMvcTest(controllers = {PublicInventoryController.class, ReservationController.class})
@Import({ReservationSecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "app.jwt.secret=" + PublicInventoryControllerTest.SECRET)
class PublicInventoryControllerTest {

    static final String SECRET = "Zmxhc2gtc2FsZS1lbmdpbmUtc2VjcmV0LWtleS0zMmIh";
    private static final Instant UPDATED = Instant.parse("2026-10-04T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservationService reservationService;

    private static InventoryResponse inventory(String ticketTypeId, int total, int available, int reserved, int sold) {
        return new InventoryResponse(ticketTypeId, "event-1", ticketTypeId, total, available, reserved, sold, UPDATED);
    }

    private static String token(String role, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder().setSubject("user-1").claim("role", role)
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), SignatureAlgorithm.HS256).compact();
    }

    @Test
    void anonymousCanReadTicketTypeAvailability() throws Exception {
        when(reservationService.getInventory("tt-vip")).thenReturn(inventory("tt-vip", 500, 350, 100, 50));

        mockMvc.perform(get("/api/v1/ticket-types/tt-vip/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketTypeId").value("tt-vip"))
                .andExpect(jsonPath("$.eventId").value("event-1"))
                .andExpect(jsonPath("$.totalQuantity").value(500))
                .andExpect(jsonPath("$.availableQuantity").value(350))
                .andExpect(jsonPath("$.reservedQuantity").value(100))
                .andExpect(jsonPath("$.soldQuantity").value(50));
    }

    @Test
    void anonymousCanReadEventAvailability() throws Exception {
        when(reservationService.getInventoryByEventId("event-1"))
                .thenReturn(List.of(inventory("tt-regular", 5000, 5000, 0, 0), inventory("tt-vip", 500, 0, 20, 480)));

        mockMvc.perform(get("/api/v1/events/event-1/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ticketTypeId").value("tt-regular"))
                .andExpect(jsonPath("$[1].availableQuantity").value(0));
    }

    @Test
    void eventWithoutInventoryReturnsEmptyList() throws Exception {
        when(reservationService.getInventoryByEventId("unknown")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/events/unknown/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void unknownTicketTypeIs404InStandardEnvelope() throws Exception {
        when(reservationService.getInventory("missing")).thenThrow(new InventoryNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/ticket-types/missing/inventory"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVENTORY_NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void readsWorkWithAnyOrNoToken() throws Exception {
        when(reservationService.getInventory("tt-vip")).thenReturn(inventory("tt-vip", 500, 350, 100, 50));

        mockMvc.perform(get("/api/v1/ticket-types/tt-vip/inventory").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ticket-types/tt-vip/inventory").header("Authorization", "Bearer " + token("CUSTOMER", -60)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ticket-types/tt-vip/inventory").header("Authorization", "Bearer " + token("CUSTOMER", 300)))
                .andExpect(status().isOk());
    }

    @Test
    void writesToInventoryPathsAreNotOpened() throws Exception {
        mockMvc.perform(post("/api/v1/ticket-types/tt-vip/inventory").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/events/event-1/inventory").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/ticket-types/tt-vip/inventory"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reservationService);
    }

    @Test
    void otherApiRoutesStayProtected() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/reservations/me").header("Authorization", "Bearer " + token("INTERNAL", 300)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reservationService);
    }

    @Test
    void publicReadDoesNotOpenInternalInventory() throws Exception {
        mockMvc.perform(get("/internal/v1/inventory/tt-vip")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reservationService);
    }
}
