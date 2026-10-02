package com.flashsale.reservation.security;

import com.flashsale.reservation.controller.InternalReservationController;
import com.flashsale.reservation.controller.InventoryController;
import com.flashsale.reservation.controller.ReservationController;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role-by-route authorization matrix:
 * /api/v1/** is for end users (CUSTOMER, ADMIN); /internal/** is for service tokens (INTERNAL) only.
 */
@WebMvcTest(controllers = {ReservationController.class, InternalReservationController.class, InventoryController.class})
@Import({ReservationSecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "app.jwt.secret=" + ReservationAuthorizationRulesTest.SECRET)
class ReservationAuthorizationRulesTest {

    static final String SECRET = "Zmxhc2gtc2FsZS1lbmdpbmUtc2VjcmV0LWtleS0zMmIh";

    private static final String RESERVATION_BODY = "{\"eventId\":\"evt-1\",\"ticketTypeId\":\"tt-1\",\"quantity\":1}";
    private static final String LIFECYCLE_BODY = "{\"orderId\":\"ord-1\",\"userId\":\"user-1\"}";
    private static final String INVENTORY_BODY = "{\"eventId\":\"evt-1\",\"ticketTypeId\":\"tt-1\",\"totalQuantity\":10,"
            + "\"availableQuantity\":10,\"reservedQuantity\":0,\"soldQuantity\":0}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservationService reservationService;

    /** Same shape as Auth Service access tokens and Order Service InternalJwtTokenProvider tokens. */
    private static String token(String subject, String role) {
        Instant now = Instant.now();
        return Jwts.builder().setSubject(subject).claim("role", role)
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), SignatureAlgorithm.HS256).compact();
    }

    private static String customer() {
        return token("user-1", "CUSTOMER");
    }

    private static String admin() {
        return token("admin-1", "ADMIN");
    }

    private static String internal() {
        return token("order-service", "INTERNAL");
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    // --- Customer-facing /api/v1/reservations/** ---

    @Test
    void customerCanCreateAndReadOwnReservations() throws Exception {
        mockMvc.perform(as(post("/api/v1/reservations"), customer())
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(RESERVATION_BODY))
                .andExpect(status().isOk());
        verify(reservationService).createReservation(eq("user-1"), eq("key-1"), any());

        mockMvc.perform(as(get("/api/v1/reservations/res-1"), customer())).andExpect(status().isOk());
        verify(reservationService).getReservationById("res-1", "user-1");

        mockMvc.perform(as(post("/api/v1/reservations/res-1/cancel"), customer())).andExpect(status().isNoContent());
        verify(reservationService).cancelReservation("res-1", "user-1");
    }

    @Test
    void adminKeepsAccessToCustomerFacingEndpoints() throws Exception {
        mockMvc.perform(as(get("/api/v1/reservations/me"), admin())).andExpect(status().isOk());
        verify(reservationService).getReservationsByUserId("admin-1");
    }

    @Test
    void internalTokenCannotUseCustomerFacingEndpoints() throws Exception {
        mockMvc.perform(as(post("/api/v1/reservations"), internal())
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(RESERVATION_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/v1/reservations/me"), internal())).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/v1/reservations/res-1"), internal())).andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/v1/reservations/res-1/cancel"), internal())).andExpect(status().isForbidden());

        verifyNoInteractions(reservationService);
    }

    @Test
    void unknownRoleCannotUseCustomerFacingEndpoints() throws Exception {
        mockMvc.perform(as(get("/api/v1/reservations/me"), token("user-1", "SUPPORT"))).andExpect(status().isForbidden());

        verifyNoInteractions(reservationService);
    }

    // --- Service-to-service /internal/** ---

    @Test
    void internalTokenCanUseInternalReservationLifecycle() throws Exception {
        mockMvc.perform(as(get("/internal/v1/reservations/res-1"), internal())).andExpect(status().isOk());
        verify(reservationService).getInternalReservationById("res-1");

        mockMvc.perform(as(post("/internal/v1/reservations/res-1/confirm"), internal())
                        .contentType(MediaType.APPLICATION_JSON).content(LIFECYCLE_BODY))
                .andExpect(status().isOk());
        verify(reservationService).confirmReservationForOrder("res-1", "ord-1", "user-1");

        mockMvc.perform(as(post("/internal/v1/reservations/res-1/cancel-after-payment-failure"), internal())
                        .contentType(MediaType.APPLICATION_JSON).content(LIFECYCLE_BODY))
                .andExpect(status().isOk());
        verify(reservationService).cancelReservationAfterPaymentFailure("res-1", "ord-1", "user-1");
    }

    @Test
    void internalTokenCanUseInventoryEndpoints() throws Exception {
        mockMvc.perform(as(get("/internal/v1/inventory/tt-1"), internal())).andExpect(status().isOk());
        verify(reservationService).getInventory("tt-1");

        mockMvc.perform(as(post("/internal/v1/inventory"), internal())
                        .contentType(MediaType.APPLICATION_JSON).content(INVENTORY_BODY))
                .andExpect(status().isOk());
        verify(reservationService).initializeInventory(any());
    }

    @Test
    void customerAndAdminCannotUseInternalEndpoints() throws Exception {
        for (String userToken : new String[]{customer(), admin()}) {
            mockMvc.perform(as(get("/internal/v1/reservations/res-1"), userToken)).andExpect(status().isForbidden());
            mockMvc.perform(as(post("/internal/v1/reservations/res-1/confirm"), userToken)
                            .contentType(MediaType.APPLICATION_JSON).content(LIFECYCLE_BODY))
                    .andExpect(status().isForbidden());
            mockMvc.perform(as(post("/internal/v1/reservations/res-1/cancel-after-payment-failure"), userToken)
                            .contentType(MediaType.APPLICATION_JSON).content(LIFECYCLE_BODY))
                    .andExpect(status().isForbidden());
            mockMvc.perform(as(get("/internal/v1/inventory/tt-1"), userToken)).andExpect(status().isForbidden());
            mockMvc.perform(as(post("/internal/v1/inventory"), userToken)
                            .contentType(MediaType.APPLICATION_JSON).content(INVENTORY_BODY))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(reservationService);
    }

    @Test
    void anonymousRequestsAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/v1/reservations/res-1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/v1/inventory/tt-1")).andExpect(status().isUnauthorized());

        verifyNoInteractions(reservationService);
    }
}
