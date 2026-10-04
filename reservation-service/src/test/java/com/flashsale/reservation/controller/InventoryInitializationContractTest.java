package com.flashsale.reservation.controller;

import com.flashsale.reservation.dto.InventoryInitializationRequest;
import com.flashsale.reservation.dto.InventoryResponse;
import com.flashsale.reservation.exception.InventoryConflictException;
import com.flashsale.reservation.security.JwtAuthenticationFilter;
import com.flashsale.reservation.security.ReservationSecurityConfig;
import com.flashsale.reservation.service.ReservationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provider side of the Catalog -> Reservation inventory setup contract: Catalog Service sends this exact
 * body to POST /internal/v1/inventory with an INTERNAL service token (sub = catalog-service).
 */
@WebMvcTest(controllers = InventoryController.class)
@Import({ReservationSecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "app.jwt.secret=" + InventoryInitializationContractTest.SECRET)
class InventoryInitializationContractTest {

    static final String SECRET = "Zmxhc2gtc2FsZS1lbmdpbmUtc2VjcmV0LWtleS0zMmIh";

    /** Must match the body sent by catalog-service ReservationInventoryClient (see its ReservationInventoryClientTest). */
    private static final String CATALOG_SETUP_BODY = """
            {"eventId":"event-1","ticketTypeId":"tt-1","totalQuantity":100,
             "availableQuantity":100,"reservedQuantity":0,"soldQuantity":0}
            """;

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

    private ResultActions setup(String body, String bearer) throws Exception {
        return mockMvc.perform(post("/internal/v1/inventory")
                .header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void catalogServiceTokenInitializesInventory() throws Exception {
        when(reservationService.initializeInventory(any())).thenReturn(
                new InventoryResponse("tt-1", "event-1", "tt-1", 100, 100, 0, 0, Instant.now()));

        setup(CATALOG_SETUP_BODY, token("catalog-service", "INTERNAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketTypeId").value("tt-1"))
                .andExpect(jsonPath("$.eventId").value("event-1"))
                .andExpect(jsonPath("$.totalQuantity").value(100));

        ArgumentCaptor<InventoryInitializationRequest> request = ArgumentCaptor.forClass(InventoryInitializationRequest.class);
        verify(reservationService).initializeInventory(request.capture());
        assertEquals("event-1", request.getValue().getEventId());
        assertEquals("tt-1", request.getValue().getTicketTypeId());
        assertEquals(100, request.getValue().getTotalQuantity());
        assertEquals(100, request.getValue().getAvailableQuantity());
        assertEquals(0, request.getValue().getReservedQuantity());
        assertEquals(0, request.getValue().getSoldQuantity());
    }

    @Test
    void conflictingSetupIs409() throws Exception {
        when(reservationService.initializeInventory(any())).thenThrow(new InventoryConflictException("different total"));

        setup(CATALOG_SETUP_BODY, token("catalog-service", "INTERNAL"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVENTORY_ALREADY_INITIALIZED"));
    }

    @Test
    void unbalancedSetupIs400() throws Exception {
        when(reservationService.initializeInventory(any()))
                .thenThrow(new IllegalArgumentException("Inventory quantities must add up to totalQuantity"));

        setup(CATALOG_SETUP_BODY, token("catalog-service", "INTERNAL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "null", "2.5", "\"many\""})
    void invalidTotalQuantityIs400BeforeReachingService(String total) throws Exception {
        setup(CATALOG_SETUP_BODY.replace("\"totalQuantity\":100", "\"totalQuantity\":" + total),
                token("catalog-service", "INTERNAL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(reservationService);
    }

    @Test
    void customerAndAdminTokensCannotInitializeInventory() throws Exception {
        setup(CATALOG_SETUP_BODY, token("user-1", "CUSTOMER")).andExpect(status().isForbidden());
        setup(CATALOG_SETUP_BODY, token("admin-1", "ADMIN")).andExpect(status().isForbidden());

        verifyNoInteractions(reservationService);
    }

    @Test
    void anonymousSetupIsUnauthorized() throws Exception {
        mockMvc.perform(post("/internal/v1/inventory").contentType(MediaType.APPLICATION_JSON).content(CATALOG_SETUP_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reservationService);
    }
}
