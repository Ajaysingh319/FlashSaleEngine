package com.flashsale.catalog.security;

import com.flashsale.catalog.controller.CatalogController;
import com.flashsale.catalog.service.EventService;
import com.flashsale.catalog.service.TicketTypeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static com.flashsale.catalog.security.CatalogTestTokens.bearer;
import static com.flashsale.catalog.security.CatalogTestTokens.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Catalog enforces ADMIN for management writes itself, so calling it directly does not bypass the Gateway. */
@WebMvcTest(controllers = CatalogController.class)
@Import(CatalogSecurityConfig.class)
class CatalogSecurityTest {

    private static final String EVENT_BODY = """
            {"name":"Fest","venue":"Stadium","city":"Delhi",
             "startTime":"2026-12-01T18:00:00Z","endTime":"2026-12-02T00:00:00Z",
             "saleStartTime":"2026-11-01T00:00:00Z","saleEndTime":"2026-12-01T17:00:00Z"}
            """;
    private static final String TICKET_TYPE_BODY = "{\"eventId\":\"evt-1\",\"name\":\"VIP\",\"price\":4999.0,\"totalQuantity\":100}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventService eventService;

    @MockBean
    private TicketTypeService ticketTypeService;

    /** Every existing management write endpoint, with a valid body so an admin request reaches the controller. */
    static Stream<Arguments> managementWrites() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/v1/events", EVENT_BODY),
                Arguments.of(HttpMethod.PUT, "/api/v1/events/evt-1", EVENT_BODY),
                Arguments.of(HttpMethod.DELETE, "/api/v1/events/evt-1", null),
                Arguments.of(HttpMethod.POST, "/api/v1/events/evt-1/cancel", null),
                Arguments.of(HttpMethod.POST, "/api/v1/ticket-types", TICKET_TYPE_BODY),
                Arguments.of(HttpMethod.PUT, "/api/v1/ticket-types/tt-1", TICKET_TYPE_BODY),
                Arguments.of(HttpMethod.DELETE, "/api/v1/ticket-types/tt-1", null));
    }

    private static MockHttpServletRequestBuilder write(HttpMethod method, String path, String body) {
        MockHttpServletRequestBuilder request = request(method, path);
        return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("managementWrites")
    void anonymousWriteIsUnauthorized(HttpMethod method, String path, String body) throws Exception {
        mockMvc.perform(write(method, path, body)).andExpect(status().isUnauthorized());
        verifyNoInteractions(eventService, ticketTypeService);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("managementWrites")
    void customerAndServiceTokensAreForbidden(HttpMethod method, String path, String body) throws Exception {
        mockMvc.perform(write(method, path, body).with(bearer(token("user-1", "CUSTOMER", 300))))
                .andExpect(status().isForbidden());
        mockMvc.perform(write(method, path, body).with(bearer(token("order-service", "INTERNAL", 300))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(eventService, ticketTypeService);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("managementWrites")
    void adminWriteReachesCatalog(HttpMethod method, String path, String body) throws Exception {
        int status = mockMvc.perform(write(method, path, body).with(bearer(token("admin-1", "ADMIN", 300))))
                .andReturn().getResponse().getStatus();

        assertThat(status).as("%s %s", method, path).isBetween(200, 299);
    }

    @Test
    void invalidAdminTokensAreUnauthorized() throws Exception {
        String otherSecret = Base64.getEncoder().encodeToString("another-flash-sale-secret-key-0123456789".getBytes());
        String forged = io.jsonwebtoken.Jwts.builder().setSubject("admin-1").claim("role", "ADMIN")
                .setExpiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(Base64.getDecoder().decode(otherSecret)),
                        io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();
        String refreshShaped = io.jsonwebtoken.Jwts.builder().setSubject("admin-1")
                .setExpiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(Base64.getDecoder().decode(CatalogTestTokens.SECRET)),
                        io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();

        for (String bad : List.of(token("admin-1", "ADMIN", -60), forged, refreshShaped, "not-a-jwt")) {
            mockMvc.perform(write(HttpMethod.POST, "/api/v1/events", EVENT_BODY).with(bearer(bad)))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(eventService, ticketTypeService);
    }

    @Test
    void patchIsAdminOnlyEvenThoughNoPatchEndpointExists() throws Exception {
        mockMvc.perform(write(HttpMethod.PATCH, "/api/v1/events/evt-1", EVENT_BODY)).andExpect(status().isUnauthorized());
        mockMvc.perform(write(HttpMethod.PATCH, "/api/v1/ticket-types/tt-1", TICKET_TYPE_BODY)
                        .with(bearer(token("user-1", "CUSTOMER", 300))))
                .andExpect(status().isForbidden());
        mockMvc.perform(write(HttpMethod.PATCH, "/api/v1/events/evt-1", EVENT_BODY)
                        .with(bearer(token("admin-1", "ADMIN", 300))))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void publicReadsNeedNoToken() throws Exception {
        when(eventService.searchEvents(any())).thenReturn(Page.empty());

        for (String path : List.of("/api/v1/events", "/api/v1/events/evt-1", "/api/v1/events/on-sale",
                "/api/v1/events/evt-1/ticket-types", "/api/v1/ticket-types/tt-1")) {
            mockMvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test
    void readsIgnoreInvalidTokensInsteadOfRejectingThem() throws Exception {
        mockMvc.perform(get("/api/v1/events/evt-1").with(bearer("not-a-jwt"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ticket-types/tt-1").with(bearer(token("user-1", "CUSTOMER", -60))))
                .andExpect(status().isOk());
    }

    @Test
    void authorizationIsCheckedBeforeValidation() throws Exception {
        mockMvc.perform(write(HttpMethod.POST, "/api/v1/ticket-types", "{\"totalQuantity\":-1}")
                        .with(bearer(token("user-1", "CUSTOMER", 300))))
                .andExpect(status().isForbidden());
        mockMvc.perform(write(HttpMethod.POST, "/api/v1/ticket-types", "{\"totalQuantity\":-1}")
                        .with(bearer(token("admin-1", "ADMIN", 300))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ticketTypeService);
    }
}
