package com.flashsale.reservation.security;

import com.flashsale.reservation.controller.InternalReservationController;
import com.flashsale.reservation.controller.ReservationController;
import com.flashsale.reservation.dto.ReservationResponse;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that Reservation Service accepts access tokens signed the way Auth Service signs them
 * and internal tokens signed the way Order Service's InternalJwtTokenProvider signs them.
 */
@WebMvcTest(controllers = {ReservationController.class, InternalReservationController.class})
@Import({ReservationSecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "app.jwt.secret=" + ReservationJwtAuthenticationTest.SECRET)
class ReservationJwtAuthenticationTest {

    static final String SECRET = "Zmxhc2gtc2FsZS1lbmdpbmUtc2VjcmV0LWtleS0zMmIh";
    private static final String OTHER_SECRET = Base64.getEncoder().encodeToString(
            "another-flash-sale-secret-key-0123456789".getBytes());

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservationService reservationService;

    /** Mirrors Auth Service JwtUtils.generateAccessToken / generateRefreshToken. */
    private static String authToken(String secret, String userId, String role, long ttlMillis) {
        var builder = Jwts.builder();
        if (role != null) {
            builder.setClaims(Map.of("role", role));
        }
        long now = System.currentTimeMillis();
        return builder.setSubject(userId)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + ttlMillis))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)), SignatureAlgorithm.HS256)
                .compact();
    }

    /** Mirrors Order Service InternalJwtTokenProvider.token(). */
    private static String orderServiceInternalToken() {
        Instant now = Instant.now();
        return Jwts.builder().setSubject("order-service").claim("role", "INTERNAL")
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), SignatureAlgorithm.HS256).compact();
    }

    @Test
    void customerAccessTokenFromAuthUsesUserIdAsPrincipal() throws Exception {
        when(reservationService.getReservationsByUserId("665f1c2ab3e4d5f6a7b8c9d0")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reservations/me")
                        .header("Authorization", "Bearer " + authToken(SECRET, "665f1c2ab3e4d5f6a7b8c9d0", "CUSTOMER", 60_000)))
                .andExpect(status().isOk());

        verify(reservationService).getReservationsByUserId("665f1c2ab3e4d5f6a7b8c9d0");
    }

    @Test
    void invalidSignatureIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/me")
                        .header("Authorization", "Bearer " + authToken(OTHER_SECRET, "user-1", "CUSTOMER", 60_000)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reservationService);
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/me")
                        .header("Authorization", "Bearer " + authToken(SECRET, "user-1", "CUSTOMER", -1_000)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reservationService);
    }

    @Test
    void refreshTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/me")
                        .header("Authorization", "Bearer " + authToken(SECRET, "user-1", null, 60_000)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reservationService);
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reservationService);
    }

    @Test
    void orderServiceInternalTokenReachesInternalEndpoint() throws Exception {
        when(reservationService.getInternalReservationById("res-1")).thenReturn(new ReservationResponse());

        mockMvc.perform(get("/internal/v1/reservations/res-1")
                        .header("Authorization", "Bearer " + orderServiceInternalToken()))
                .andExpect(status().isOk());

        verify(reservationService).getInternalReservationById("res-1");
    }

    @Test
    void getReservationByIdBindsIdAndUserId() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/res-1")
                        .header("Authorization", "Bearer " + authToken(SECRET, "user-1", "CUSTOMER", 60_000)))
                .andExpect(status().isOk());

        verify(reservationService).getReservationById("res-1", "user-1");
    }

    @Test
    void customerTokenCannotReachInternalEndpoint() throws Exception {
        mockMvc.perform(get("/internal/v1/reservations/res-1")
                        .header("Authorization", "Bearer " + authToken(SECRET, "user-1", "CUSTOMER", 60_000)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reservationService);
    }
}
