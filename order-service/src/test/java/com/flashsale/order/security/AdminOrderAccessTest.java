package com.flashsale.order.security;

import com.flashsale.order.controller.AdminOrderController;
import com.flashsale.order.document.OrderStatus;
import com.flashsale.order.dto.PageResponse;
import com.flashsale.order.service.AdminOrderQueryService;
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

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/v1/admin/orders is ADMIN-only (PRD 6.14); filters and paging reach the query service unchanged. */
@WebMvcTest(controllers = AdminOrderController.class)
@Import({OrderSecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "app.jwt.secret=" + OrderJwtAuthenticationTest.SECRET)
class AdminOrderAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminOrderQueryService adminOrderQueryService;

    private static String bearer(String role) {
        long now = System.currentTimeMillis();
        return "Bearer " + Jwts.builder().setClaims(Map.of("role", role)).setSubject("user-1")
                .setIssuedAt(new Date(now)).setExpiration(new Date(now + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(OrderJwtAuthenticationTest.SECRET)), SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void adminListsOrdersWithFiltersAndPaging() throws Exception {
        when(adminOrderQueryService.findOrders("event-1", OrderStatus.CONFIRMED, 2, 50))
                .thenReturn(new PageResponse<>(List.of(), 2, 50, 101, 3));

        mockMvc.perform(get("/api/v1/admin/orders?eventId=event-1&status=CONFIRMED&page=2&size=50")
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(101))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void defaultsToTheFirstPageOfTwenty() throws Exception {
        when(adminOrderQueryService.findOrders(null, null, 0, 20)).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/admin/orders").header("Authorization", bearer("ADMIN"))).andExpect(status().isOk());
        verify(adminOrderQueryService).findOrders(null, null, 0, 20);
    }

    @Test
    void customersAndInternalCallersAreForbidden() throws Exception {
        for (String role : List.of("CUSTOMER", "INTERNAL")) {
            mockMvc.perform(get("/api/v1/admin/orders").header("Authorization", bearer(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(adminOrderQueryService);
    }

    @Test
    void anonymousCallerIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders")).andExpect(status().isUnauthorized());
        verifyNoInteractions(adminOrderQueryService);
    }

    @Test
    void unknownStatusIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders?status=BOGUS").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isBadRequest());
        verify(adminOrderQueryService, never()).findOrders(any(), any(), anyInt(), anyInt());
    }

    @Test
    void adminCannotUseCustomerOrderEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/orders/me").header("Authorization", bearer("ADMIN"))).andExpect(status().isForbidden());
    }
}
