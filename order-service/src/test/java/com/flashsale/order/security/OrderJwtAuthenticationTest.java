package com.flashsale.order.security;

import com.flashsale.order.controller.OrderController;
import com.flashsale.order.service.InternalJwtTokenProvider;
import com.flashsale.order.dto.PaymentInitiationRequest;
import com.flashsale.order.dto.PaymentInitiationResponse;
import com.flashsale.order.service.OrderPaymentService;
import com.flashsale.order.service.OrderService;
import org.springframework.http.MediaType;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that Order Service accepts access tokens signed the way Auth Service signs them
 * (Base64-decoded JWT_SECRET, HS256, sub = user ID, role claim) and rejects everything else.
 */
@WebMvcTest(controllers = OrderController.class)
@Import({OrderSecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "app.jwt.secret=" + OrderJwtAuthenticationTest.SECRET)
class OrderJwtAuthenticationTest {

    static final String SECRET = "Zmxhc2gtc2FsZS1lbmdpbmUtc2VjcmV0LWtleS0zMmIh";
    private static final String OTHER_SECRET = Base64.getEncoder().encodeToString(
            "another-flash-sale-secret-key-0123456789".getBytes());

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OrderPaymentService orderPaymentService;

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

    @Test
    void customerAccessTokenFromAuthReachesOrdersWithUserIdAsPrincipal() throws Exception {
        when(orderService.getMyOrders("665f1c2ab3e4d5f6a7b8c9d0")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/orders/me")
                        .header("Authorization", "Bearer " + authToken(SECRET, "665f1c2ab3e4d5f6a7b8c9d0", "CUSTOMER", 60_000)))
                .andExpect(status().isOk());

        verify(orderService).getMyOrders("665f1c2ab3e4d5f6a7b8c9d0");
    }

    @Test
    void getOrderByIdBindsOrderIdAndUserId() throws Exception {
        mockMvc.perform(get("/api/v1/orders/ord-123")
                        .header("Authorization", "Bearer " + authToken(SECRET, "user-1", "CUSTOMER", 60_000)))
                .andExpect(status().isOk());

        verify(orderService).getOrder("ord-123", "user-1");
    }

    @Test
    void cancelOrderBindsOrderIdAndUserId() throws Exception {
        mockMvc.perform(post("/api/v1/orders/ord-123/cancel")
                        .header("Authorization", "Bearer " + authToken(SECRET, "user-1", "CUSTOMER", 60_000)))
                .andExpect(status().isOk());

        verify(orderService).cancelOrder("ord-123", "user-1");
    }

    // --- POST /api/v1/orders/{orderId}/payment (PRD 6.8, TDD 48) ---

    @Test
    void paymentInitiationIsAcceptedAsProcessing() throws Exception {
        when(orderPaymentService.initiatePayment(eq("user-1"), eq("key-1"), eq("ord-123"), any()))
                .thenReturn(new PaymentInitiationResponse("pay-1", "ord-123", "PROCESSING"));

        mockMvc.perform(post("/api/v1/orders/ord-123/payment")
                        .header("Authorization", "Bearer " + authToken(SECRET, "user-1", "CUSTOMER", 60_000))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"MOCK_CARD\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.paymentId").value("pay-1"))
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        verify(orderPaymentService).initiatePayment("user-1", "key-1", "ord-123", new PaymentInitiationRequest("MOCK_CARD"));
    }

    @Test
    void paymentInitiationRequiresKeyAndSupportedMethod() throws Exception {
        String token = "Bearer " + authToken(SECRET, "user-1", "CUSTOMER", 60_000);

        mockMvc.perform(post("/api/v1/orders/ord-123/payment").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"MOCK_CARD\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/orders/ord-123/payment").header("Authorization", token).header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"REAL_VISA\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/orders/ord-123/payment").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"MOCK_CARD\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orderPaymentService);
    }

    @Test
    void nonCustomerRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/orders/me")
                        .header("Authorization", "Bearer " + authToken(SECRET, "admin-1", "ADMIN", 60_000)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    @Test
    void invalidSignatureIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orders/me")
                        .header("Authorization", "Bearer " + authToken(OTHER_SECRET, "user-1", "CUSTOMER", 60_000)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orderService);
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orders/me")
                        .header("Authorization", "Bearer " + authToken(SECRET, "user-1", "CUSTOMER", -1_000)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orderService);
    }

    @Test
    void refreshTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orders/me")
                        .header("Authorization", "Bearer " + authToken(SECRET, "user-1", null, 60_000)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orderService);
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orders/me")).andExpect(status().isUnauthorized());
        verify(orderService, never()).getMyOrders(anyString());
    }

    @Test
    void internalServiceTokenUsesSameSecretAndAlgorithm() {
        InternalJwtTokenProvider provider = new InternalJwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", SECRET);
        ReflectionTestUtils.invokeMethod(provider, "initialize");

        var jws = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET))).build()
                .parseClaimsJws(provider.token());

        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(jws.getBody().getSubject()).isEqualTo("order-service");
        assertThat(jws.getBody().get("role", String.class)).isEqualTo("INTERNAL");
    }
}
