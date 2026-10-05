package com.flashsale.reservation.security;

import com.flashsale.reservation.controller.AdminController;
import com.flashsale.reservation.dto.EventStatisticsResponse;
import com.flashsale.reservation.dto.InventoryTotals;
import com.flashsale.reservation.dto.PageResponse;
import com.flashsale.reservation.dto.StatisticsResponse;
import com.flashsale.reservation.exception.EventNotFoundException;
import com.flashsale.reservation.service.AdminReservationQueryService;
import com.flashsale.reservation.service.AdminStatisticsService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The /api/v1/admin/** monitoring API is ADMIN-only (PRD 6.14) and returns the load-test proof fields. */
@WebMvcTest(controllers = AdminController.class)
@Import({ReservationSecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "app.jwt.secret=" + ReservationAuthorizationRulesTest.SECRET)
class AdminAccessTest {
    private static final List<String> ADMIN_PATHS = List.of(
            "/api/v1/admin/reservations/active", "/api/v1/admin/events/evt-1/statistics", "/api/v1/admin/statistics");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminReservationQueryService adminReservationQueryService;

    @MockBean
    private AdminStatisticsService adminStatisticsService;

    private static String bearer(String role) {
        Instant now = Instant.now();
        return "Bearer " + Jwts.builder().setSubject("user-1").claim("role", role)
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(ReservationAuthorizationRulesTest.SECRET)),
                        SignatureAlgorithm.HS256).compact();
    }

    @Test
    void adminSeesEventStatisticsWithTheOversellingProof() throws Exception {
        InventoryTotals inventory = new InventoryTotals(100, 0, 0, 100);
        when(adminStatisticsService.eventStatistics("evt-1")).thenReturn(new EventStatisticsResponse(
                "evt-1", inventory, List.of(), Map.of(), new BigDecimal("499900.00")));

        mockMvc.perform(get("/api/v1/admin/events/evt-1/statistics").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inventory.sold").value(100))
                .andExpect(jsonPath("$.inventory.oversold").value(0))
                .andExpect(jsonPath("$.inventory.consistent").value(true))
                .andExpect(jsonPath("$.confirmedRevenue").value(499900.00));
    }

    @Test
    void adminSeesPlatformStatisticsAndActiveReservations() throws Exception {
        when(adminStatisticsService.statistics())
                .thenReturn(new StatisticsResponse(InventoryTotals.EMPTY, Map.of(), BigDecimal.ZERO, List.of()));
        when(adminReservationQueryService.findActiveReservations("evt-1", 1, 50))
                .thenReturn(new PageResponse<>(List.of(), 1, 50, 0, 0));

        mockMvc.perform(get("/api/v1/admin/statistics").header("Authorization", bearer("ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/reservations/active?eventId=evt-1&page=1&size=50")
                .header("Authorization", bearer("ADMIN"))).andExpect(status().isOk());
        verify(adminReservationQueryService).findActiveReservations("evt-1", 1, 50);
    }

    @Test
    void customersAndServiceTokensAreForbidden() throws Exception {
        for (String role : List.of("CUSTOMER", "INTERNAL")) {
            for (String path : ADMIN_PATHS) {
                mockMvc.perform(get(path).header("Authorization", bearer(role))).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(adminReservationQueryService, adminStatisticsService);
    }

    @Test
    void anonymousCallerIsUnauthorized() throws Exception {
        for (String path : ADMIN_PATHS) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(adminReservationQueryService, adminStatisticsService);
    }

    @Test
    void unknownEventIsNotFound() throws Exception {
        when(adminStatisticsService.eventStatistics("missing")).thenThrow(new EventNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/admin/events/missing/statistics").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNotFound());
    }
}
