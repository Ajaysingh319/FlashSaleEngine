package com.flashsale.catalog.controller;

import com.flashsale.catalog.service.EventService;
import com.flashsale.catalog.service.TicketTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Path IDs must bind without relying on -parameters compiler metadata. */
@WebMvcTest(controllers = CatalogController.class)
class CatalogControllerPathVariableTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventService eventService;

    @MockBean
    private TicketTypeService ticketTypeService;

    @Test
    void getEventByIdBindsId() throws Exception {
        mockMvc.perform(get("/api/v1/events/evt-123")).andExpect(status().isOk());

        verify(eventService).getEventById("evt-123");
    }

    @Test
    void getTicketTypeByIdBindsId() throws Exception {
        mockMvc.perform(get("/api/v1/ticket-types/tt-456")).andExpect(status().isOk());

        verify(ticketTypeService).getTicketTypeById("tt-456");
    }

    @Test
    void getTicketTypesByEventIdBindsEventId() throws Exception {
        mockMvc.perform(get("/api/v1/events/evt-123/ticket-types")).andExpect(status().isOk());

        verify(ticketTypeService).getTicketTypesByEventId("evt-123");
    }
}
