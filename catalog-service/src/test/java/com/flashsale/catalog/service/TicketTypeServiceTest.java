package com.flashsale.catalog.service;

import com.flashsale.catalog.document.Event;
import com.flashsale.catalog.document.TicketType;
import com.flashsale.catalog.dto.TicketTypeRequest;
import com.flashsale.catalog.dto.TicketTypeResponse;
import com.flashsale.catalog.repository.EventRepository;
import com.flashsale.catalog.repository.TicketTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketTypeServiceTest {

    @Mock
    private TicketTypeRepository ticketTypeRepository;

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private TicketTypeService ticketTypeService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateTicketType() {
        TicketTypeRequest request = new TicketTypeRequest();
        request.setName("VIP");
        request.setPrice(100.0);
        request.setTotalQuantity(100);
        request.setAvailableQuantity(100);
        request.setReservedQuantity(0);
        request.setSoldQuantity(0);
        request.setEventId("eventId");

        Event event = new Event();
        event.setId("eventId");
        event.setName("Test Event");

        TicketType savedTicketType = new TicketType();
        savedTicketType.setId("ticketTypeId");
        savedTicketType.setName(request.getName());
        savedTicketType.setPrice(request.getPrice());
        savedTicketType.setTotalQuantity(request.getTotalQuantity());
        savedTicketType.setAvailableQuantity(request.getAvailableQuantity());
        savedTicketType.setReservedQuantity(request.getReservedQuantity());
        savedTicketType.setSoldQuantity(request.getSoldQuantity());
        savedTicketType.setEvent(event);
        savedTicketType.setCreatedAt(Instant.now());
        savedTicketType.setUpdatedAt(Instant.now());

        when(eventRepository.findById(request.getEventId())).thenReturn(Optional.of(event));
        when(ticketTypeRepository.save(any(TicketType.class))).thenReturn(savedTicketType);

        TicketTypeResponse response = ticketTypeService.createTicketType(request);

        assertNotNull(response);
        assertEquals("ticketTypeId", response.getId());
        assertEquals(request.getName(), response.getName());
        assertEquals(request.getPrice(), response.getPrice());
        assertEquals(request.getTotalQuantity(), response.getTotalQuantity());
        assertEquals(request.getAvailableQuantity(), response.getAvailableQuantity());
        assertEquals(request.getReservedQuantity(), response.getReservedQuantity());
        assertEquals(request.getSoldQuantity(), response.getSoldQuantity());
        assertEquals(request.getEventId(), response.getEventId());
    }

    @Test
    void testGetTicketTypeById() {
        String id = "ticketTypeId";
        Event event = new Event();
        event.setId("eventId");

        TicketType ticketType = new TicketType();
        ticketType.setId(id);
        ticketType.setName("VIP");
        ticketType.setPrice(100.0);
        ticketType.setTotalQuantity(100);
        ticketType.setAvailableQuantity(100);
        ticketType.setReservedQuantity(0);
        ticketType.setSoldQuantity(0);
        ticketType.setEvent(event);
        ticketType.setCreatedAt(Instant.now());
        ticketType.setUpdatedAt(Instant.now());

        when(ticketTypeRepository.findById(id)).thenReturn(Optional.of(ticketType));

        TicketTypeResponse response = ticketTypeService.getTicketTypeById(id);

        assertNotNull(response);
        assertEquals(id, response.getId());
        assertEquals("VIP", response.getName());
        assertEquals(100.0, response.getPrice());
        assertEquals(100, response.getTotalQuantity());
        assertEquals(100, response.getAvailableQuantity());
        assertEquals(0, response.getReservedQuantity());
        assertEquals(0, response.getSoldQuantity());
        assertEquals("eventId", response.getEventId());
    }

    @Test
    void testGetTicketTypesByEventId() {
        String eventId = "eventId";
        Event event = new Event();
        event.setId(eventId);

        TicketType tt1 = new TicketType();
        tt1.setId("1");
        tt1.setName("VIP");
        tt1.setPrice(100.0);
        tt1.setTotalQuantity(100);
        tt1.setAvailableQuantity(100);
        tt1.setReservedQuantity(0);
        tt1.setSoldQuantity(0);
        tt1.setEvent(event);

        TicketType tt2 = new TicketType();
        tt2.setId("2");
        tt2.setName("Regular");
        tt2.setPrice(50.0);
        tt2.setTotalQuantity(200);
        tt2.setAvailableQuantity(200);
        tt2.setReservedQuantity(0);
        tt2.setSoldQuantity(0);
        tt2.setEvent(event);

        when(ticketTypeRepository.findByEventId(eventId)).thenReturn(Arrays.asList(tt1, tt2));

        List<TicketTypeResponse> responses = ticketTypeService.getTicketTypesByEventId(eventId);

        assertEquals(2, responses.size());
        assertEquals("VIP", responses.get(0).getName());
        assertEquals("Regular", responses.get(1).getName());
    }

    @Test
    void testUpdateTicketType() {
        String id = "ticketTypeId";
        Event event = new Event();
        event.setId("eventId");

        TicketType existing = new TicketType();
        existing.setId(id);
        existing.setName("VIP");
        existing.setPrice(100.0);
        existing.setTotalQuantity(100);
        existing.setAvailableQuantity(100);
        existing.setReservedQuantity(0);
        existing.setSoldQuantity(0);
        existing.setEvent(event);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        TicketTypeRequest request = new TicketTypeRequest();
        request.setName("VIP Updated");
        request.setPrice(150.0);
        request.setTotalQuantity(150);
        request.setAvailableQuantity(150);
        request.setReservedQuantity(0);
        request.setSoldQuantity(0);
        request.setEventId("eventId");

        TicketType updated = new TicketType();
        updated.setId(id);
        updated.setName(request.getName());
        updated.setPrice(request.getPrice());
        updated.setTotalQuantity(request.getTotalQuantity());
        updated.setAvailableQuantity(request.getAvailableQuantity());
        updated.setReservedQuantity(request.getReservedQuantity());
        updated.setSoldQuantity(request.getSoldQuantity());
        updated.setEvent(event);
        updated.setCreatedAt(existing.getCreatedAt());
        updated.setUpdatedAt(Instant.now());

        when(ticketTypeRepository.findById(id)).thenReturn(Optional.of(existing));
        when(eventRepository.findById(request.getEventId())).thenReturn(Optional.of(event));
        when(ticketTypeRepository.save(any(TicketType.class))).thenReturn(updated);

        TicketTypeResponse response = ticketTypeService.updateTicketType(id, request);

        assertNotNull(response);
        assertEquals(request.getName(), response.getName());
        assertEquals(request.getPrice(), response.getPrice());
        assertEquals(request.getTotalQuantity(), response.getTotalQuantity());
        assertEquals(request.getAvailableQuantity(), response.getAvailableQuantity());
        assertEquals(request.getReservedQuantity(), response.getReservedQuantity());
        assertEquals(request.getSoldQuantity(), response.getSoldQuantity());
        assertEquals(request.getEventId(), response.getEventId());
    }

    @Test
    void testDeleteTicketType() {
        String id = "ticketTypeId";
        doNothing().when(ticketTypeRepository).deleteById(id);

        ticketTypeService.deleteTicketType(id);

        verify(ticketTypeRepository, times(1)).deleteById(id);
    }
}