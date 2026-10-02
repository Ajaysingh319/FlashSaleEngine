package com.flashsale.catalog.controller;

import com.flashsale.catalog.document.EventStatus;
import com.flashsale.catalog.dto.EventRequest;
import com.flashsale.catalog.dto.EventResponse;
import com.flashsale.catalog.dto.EventSearchRequest;
import com.flashsale.catalog.exception.EventNotFoundException;
import com.flashsale.catalog.service.EventService;
import com.flashsale.catalog.service.TicketTypeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CatalogController.class)
class CatalogControllerEventDiscoveryTest {

    private static final String VALID_EVENT_BODY = """
            {"name":"Delhi Music Festival","description":"Two days","venue":"JLN Stadium","city":"Delhi",
             "startTime":"2026-12-01T18:00:00Z","endTime":"2026-12-02T00:00:00Z",
             "saleStartTime":"2026-11-01T00:00:00Z","saleEndTime":"2026-12-01T17:00:00Z","status":"UPCOMING"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventService eventService;

    @MockBean
    private TicketTypeService ticketTypeService;

    private static EventResponse event(String id, String name) {
        return new EventResponse(id, name, "desc", "venue", "Delhi",
                Instant.parse("2026-12-01T18:00:00Z"), Instant.parse("2026-12-02T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-12-01T17:00:00Z"),
                "ON_SALE", Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"));
    }

    private EventSearchRequest capturedSearch() {
        ArgumentCaptor<EventSearchRequest> captor = ArgumentCaptor.forClass(EventSearchRequest.class);
        verify(eventService).searchEvents(captor.capture());
        return captor.getValue();
    }

    // --- Discovery: GET /api/v1/events ---

    @Test
    void listWithoutParametersUsesDefaultsAndReturnsArrayWithPagingHeaders() throws Exception {
        when(eventService.searchEvents(any())).thenReturn(
                new PageImpl<>(List.of(event("evt-1", "Delhi Music Festival")), PageRequest.of(0, 20), 41));

        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "41"))
                .andExpect(header().string("X-Total-Pages", "3"))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value("evt-1"))
                .andExpect(jsonPath("$[0].name").value("Delhi Music Festival"))
                .andExpect(jsonPath("$[0].venue").value("venue"))
                .andExpect(jsonPath("$[0].city").value("Delhi"))
                .andExpect(jsonPath("$[0].saleStartTime").exists())
                .andExpect(jsonPath("$[0].status").value("ON_SALE"));

        EventSearchRequest search = capturedSearch();
        assertNull(search.getQ());
        assertNull(search.getStatus());
        assertNull(search.getCity());
        assertEquals("startTime", search.getSortBy());
        assertEquals("asc", search.getDirection());
        assertEquals(0, search.getPage());
        assertEquals(20, search.getSize());
    }

    @Test
    void bindsSearchFiltersSortingAndPaging() throws Exception {
        when(eventService.searchEvents(any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 0));

        mockMvc.perform(get("/api/v1/events")
                        .param("q", "music")
                        .param("status", "ON_SALE")
                        .param("city", "Delhi")
                        .param("startFrom", "2026-11-01T00:00:00Z")
                        .param("startTo", "2026-12-31T23:59:59Z")
                        .param("sortBy", "saleStartTime")
                        .param("direction", "desc")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        EventSearchRequest search = capturedSearch();
        assertEquals("music", search.getQ());
        assertEquals(EventStatus.ON_SALE, search.getStatus());
        assertEquals("Delhi", search.getCity());
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), search.getStartFrom());
        assertEquals(Instant.parse("2026-12-31T23:59:59Z"), search.getStartTo());
        assertEquals("saleStartTime", search.getSortBy());
        assertEquals("desc", search.getDirection());
        assertEquals(1, search.getPage());
        assertEquals(5, search.getSize());
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({
            "status, PUBLISHED",
            "status, on_sale",
            "sortBy, password",
            "sortBy, _id",
            "sortBy, ''",
            "direction, sideways",
            "page, -1",
            "page, abc",
            "size, 0",
            "size, 101",
            "startFrom, yesterday",
            "startTo, 2026-13-45"
    })
    void rejectsInvalidQueryParameters(String name, String value) throws Exception {
        mockMvc.perform(get("/api/v1/events").param(name, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message", containsString(name)))
                .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(eventService);
    }

    @Test
    void rejectsStartRangeWhereFromIsAfterTo() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                        .param("startFrom", "2026-12-31T00:00:00Z")
                        .param("startTo", "2026-11-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message", containsString("startFrom must not be after startTo")));

        verifyNoInteractions(eventService);
    }

    @Test
    void rejectsOverlongSearchText() throws Exception {
        mockMvc.perform(get("/api/v1/events").param("q", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(eventService);
    }

    // --- Regression: existing event endpoints ---

    @Test
    void getEventByIdReturnsEventDetails() throws Exception {
        when(eventService.getEventById("evt-1")).thenReturn(event("evt-1", "Delhi Music Festival"));

        mockMvc.perform(get("/api/v1/events/evt-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("evt-1"))
                .andExpect(jsonPath("$.description").value("desc"))
                .andExpect(jsonPath("$.endTime").exists())
                .andExpect(jsonPath("$.saleEndTime").exists());
    }

    @Test
    void getUnknownEventReturnsEventNotFound() throws Exception {
        when(eventService.getEventById("missing")).thenThrow(new EventNotFoundException("Event not found: missing"));

        mockMvc.perform(get("/api/v1/events/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void onSaleEndpointIsUnchangedAndNotTreatedAsAnId() throws Exception {
        when(eventService.getEventsOnSale()).thenReturn(List.of(event("evt-1", "Live now")));

        mockMvc.perform(get("/api/v1/events/on-sale"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Live now"));

        verify(eventService, never()).getEventById(any());
    }

    @Test
    void createValidEventSucceeds() throws Exception {
        when(eventService.createEvent(any())).thenReturn(event("evt-new", "Delhi Music Festival"));

        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("evt-new"));

        verify(eventService).createEvent(any(EventRequest.class));
    }

    @Test
    void createRejectsEndTimeNotAfterStartTime() throws Exception {
        String body = VALID_EVENT_BODY.replace("\"endTime\":\"2026-12-02T00:00:00Z\"", "\"endTime\":\"2026-12-01T18:00:00Z\"");

        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message", containsString("endTime must be after startTime")));

        verifyNoInteractions(eventService);
    }

    @Test
    void createRejectsSaleEndBeforeSaleStart() throws Exception {
        String body = VALID_EVENT_BODY.replace("\"saleEndTime\":\"2026-12-01T17:00:00Z\"", "\"saleEndTime\":\"2026-10-01T00:00:00Z\"");

        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message", containsString("saleEndTime must not be before saleStartTime")));

        verifyNoInteractions(eventService);
    }

    @Test
    void createRejectsInvalidStatusAndMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT_BODY.replace("\"UPCOMING\"", "\"LIVE\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message", containsString("status")));

        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Only a name\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message", containsString("venue")))
                .andExpect(jsonPath("$.error.message", containsString("startTime")));

        verifyNoInteractions(eventService);
    }

    @Test
    void createRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void updateValidatesAndBindsId() throws Exception {
        when(eventService.updateEvent(eq("evt-1"), any())).thenReturn(event("evt-1", "Renamed"));

        mockMvc.perform(put("/api/v1/events/evt-1").contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));

        mockMvc.perform(put("/api/v1/events/evt-1").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT_BODY.replace("\"endTime\":\"2026-12-02T00:00:00Z\"", "\"endTime\":\"2026-11-01T00:00:00Z\"")))
                .andExpect(status().isBadRequest());

        verify(eventService, times(1)).updateEvent(eq("evt-1"), any());
    }

    @Test
    void updateOfUnknownEventReturnsEventNotFound() throws Exception {
        when(eventService.updateEvent(eq("missing"), any())).thenThrow(new EventNotFoundException("Event not found: missing"));

        mockMvc.perform(put("/api/v1/events/missing").contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void deleteEventIsUnchanged() throws Exception {
        mockMvc.perform(delete("/api/v1/events/evt-1")).andExpect(status().isNoContent());

        verify(eventService).deleteEvent("evt-1");
    }
}
