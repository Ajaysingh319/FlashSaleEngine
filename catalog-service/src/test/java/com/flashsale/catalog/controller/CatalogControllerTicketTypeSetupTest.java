package com.flashsale.catalog.controller;

import com.flashsale.catalog.security.CatalogSecurityConfig;
import org.springframework.context.annotation.Import;

import static com.flashsale.catalog.security.CatalogTestTokens.asAdmin;

import com.flashsale.catalog.dto.TicketTypeRequest;
import com.flashsale.catalog.dto.TicketTypeResponse;
import com.flashsale.catalog.exception.InventoryProvisioningException;
import com.flashsale.catalog.exception.TicketTypeConflictException;
import com.flashsale.catalog.exception.TicketTypeNotFoundException;
import com.flashsale.catalog.service.EventService;
import com.flashsale.catalog.service.TicketTypeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CatalogController.class)
@Import(CatalogSecurityConfig.class)
class CatalogControllerTicketTypeSetupTest {

    private static final String VALID_BODY = "{\"eventId\":\"event-1\",\"name\":\"VIP\",\"price\":4999.0,\"totalQuantity\":500}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TicketTypeService ticketTypeService;

    @MockBean
    private EventService eventService;

    private ResultActions createTicketType(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/ticket-types").with(asAdmin()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static TicketTypeResponse ready() {
        return new TicketTypeResponse("tt-1", "VIP", 4999.0, 500, "READY", "event-1", Instant.now(), Instant.now());
    }

    @Test
    void successfulSetupReturnsReadyTicketType() throws Exception {
        when(ticketTypeService.createTicketType(any())).thenReturn(ready());

        createTicketType(VALID_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("tt-1"))
                .andExpect(jsonPath("$.totalQuantity").value(500))
                .andExpect(jsonPath("$.inventoryStatus").value("READY"))
                .andExpect(jsonPath("$.availableQuantity").doesNotExist());

        ArgumentCaptor<TicketTypeRequest> request = ArgumentCaptor.forClass(TicketTypeRequest.class);
        verify(ticketTypeService).createTicketType(request.capture());
        assertEquals(500, request.getValue().getTotalQuantity());
    }

    @Test
    void clientCannotSetAvailableReservedOrSoldCounts() throws Exception {
        when(ticketTypeService.createTicketType(any())).thenReturn(ready());

        createTicketType(VALID_BODY.replace("}", ",\"availableQuantity\":9999,\"soldQuantity\":-5}"))
                .andExpect(status().isOk());

        ArgumentCaptor<TicketTypeRequest> request = ArgumentCaptor.forClass(TicketTypeRequest.class);
        verify(ticketTypeService).createTicketType(request.capture());
        assertEquals(500, request.getValue().getTotalQuantity());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "null"})
    void nonPositiveTotalQuantityIsRejectedBeforeSetup(String total) throws Exception {
        createTicketType(VALID_BODY.replace("\"totalQuantity\":500", "\"totalQuantity\":" + total))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message", containsString("totalQuantity")));

        verifyNoInteractions(ticketTypeService);
    }

    /** Rejected while parsing the body (accept-float-as-int is disabled), so no fractional stock is truncated. */
    @ParameterizedTest
    @ValueSource(strings = {"2.5", "\"many\""})
    void nonIntegerTotalQuantityIsRejectedBeforeSetup(String total) throws Exception {
        createTicketType(VALID_BODY.replace("\"totalQuantity\":500", "\"totalQuantity\":" + total))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(ticketTypeService);
    }

    @Test
    void missingTotalQuantityIsRejected() throws Exception {
        createTicketType("{\"eventId\":\"event-1\",\"name\":\"VIP\",\"price\":4999.0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message", containsString("totalQuantity")));

        verifyNoInteractions(ticketTypeService);
    }

    @Test
    void nonPositivePriceAndBlankNameAreRejected() throws Exception {
        createTicketType(VALID_BODY.replace("4999.0", "0")).andExpect(status().isBadRequest());
        createTicketType(VALID_BODY.replace("\"VIP\"", "\" \"")).andExpect(status().isBadRequest());

        verifyNoInteractions(ticketTypeService);
    }

    @Test
    void reservationFailureIs503AndDoesNotExposeInternals() throws Exception {
        when(ticketTypeService.createTicketType(any()))
                .thenThrow(new InventoryProvisioningException("Could not initialize inventory for ticket type tt-1",
                        new RuntimeException("I/O error on POST request for http://reservation-service:8083/internal/v1/inventory")));

        createTicketType(VALID_BODY)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message", containsString("Retry the same request")))
                .andExpect(jsonPath("$.error.message", not(containsString("reservation-service:8083"))));
    }

    @Test
    void conflictingSetupIs409() throws Exception {
        when(ticketTypeService.createTicketType(any()))
                .thenThrow(new TicketTypeConflictException("Ticket type 'VIP' already exists with a different total quantity"));

        createTicketType(VALID_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TICKET_TYPE_CONFLICT"));
    }

    @Test
    void changingTotalQuantityOnUpdateIs409() throws Exception {
        when(ticketTypeService.updateTicketType(eq("tt-1"), any()))
                .thenThrow(new TicketTypeConflictException("totalQuantity of ticket type tt-1 cannot be changed after setup"));

        mockMvc.perform(put("/api/v1/ticket-types/tt-1").with(asAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("500", "1000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TICKET_TYPE_CONFLICT"));
    }

    @Test
    void unknownTicketTypeIs404() throws Exception {
        when(ticketTypeService.getTicketTypeById("missing")).thenThrow(new TicketTypeNotFoundException("Ticket type not found: missing"));

        mockMvc.perform(get("/api/v1/ticket-types/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TICKET_TYPE_NOT_FOUND"));
    }
}
