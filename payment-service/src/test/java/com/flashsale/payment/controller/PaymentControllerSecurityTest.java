package com.flashsale.payment.controller;

import com.flashsale.payment.document.Payment;
import com.flashsale.payment.repository.PaymentRepository;
import com.flashsale.payment.security.JwtAuthenticationFilter;
import com.flashsale.payment.security.PaymentSecurityConfig;
import com.flashsale.payment.service.PaymentQueryService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/v1/payments/{paymentId}: owner or ADMIN only, through the real security chain and query service. */
@WebMvcTest(controllers = PaymentController.class)
@Import({PaymentSecurityConfig.class, JwtAuthenticationFilter.class, PaymentQueryService.class})
@TestPropertySource(properties = "app.jwt.secret=" + PaymentControllerSecurityTest.SECRET)
class PaymentControllerSecurityTest {

    static final String SECRET = "Zmxhc2gtc2FsZS1lbmdpbmUtc2VjcmV0LWtleS0zMmIh";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        when(paymentRepository.findByPaymentId("pay-1")).thenReturn(Optional.of(
                Payment.start("pay-1", "order-1", "user-1", new BigDecimal("9998.00"), "MOCK_CARD", Instant.now())));
        when(paymentRepository.findByPaymentId("missing")).thenReturn(Optional.empty());
    }

    private static String token(String userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder().setSubject(userId).claim("role", role)
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), SignatureAlgorithm.HS256).compact();
    }

    private ResultActions getAs(String path, String userId, String role) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token(userId, role)));
    }

    @Test
    void ownerSeesTheirPayment() throws Exception {
        getAs("/api/v1/payments/pay-1", "user-1", "CUSTOMER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value("pay-1"))
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.amount").value(9998.00))
                .andExpect(jsonPath("$.paymentMethod").value("MOCK_CARD"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void anotherCustomerIsForbidden() throws Exception {
        getAs("/api/v1/payments/pay-1", "user-2", "CUSTOMER")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanMonitorAnyPayment() throws Exception {
        getAs("/api/v1/payments/pay-1", "admin-1", "ADMIN").andExpect(status().isOk());
    }

    @Test
    void unknownPaymentIs404() throws Exception {
        getAs("/api/v1/payments/missing", "user-1", "CUSTOMER")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void anonymousServiceTokensAndOtherEndpointsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/payments/pay-1")).andExpect(status().isUnauthorized());
        getAs("/api/v1/payments/pay-1", "order-service", "INTERNAL").andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/payments/pay-1").header("Authorization", "Bearer " + token("admin-1", "ADMIN")))
                .andExpect(status().isForbidden());
    }
}
