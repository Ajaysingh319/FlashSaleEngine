package com.flashsale.catalog.service;

import com.flashsale.catalog.document.Event;
import com.flashsale.catalog.dto.EventRequest;
import com.flashsale.catalog.dto.EventResponse;
import com.flashsale.catalog.exception.EventNotFoundException;
import com.flashsale.catalog.exception.EventStateConflictException;
import com.flashsale.catalog.repository.EventRepository;
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

class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateEvent() {
        EventRequest request = new EventRequest();
        request.setName("Test Event");
        request.setDescription("Description");
        request.setVenue("Venue");
        request.setCity("City");
        request.setStartTime(Instant.now().plusSeconds(3600));
        request.setEndTime(Instant.now().plusSeconds(7200));
        request.setSaleStartTime(Instant.now());
        request.setSaleEndTime(Instant.now().plusSeconds(10000));
        request.setStatus("DRAFT");

        Event savedEvent = new Event();
        savedEvent.setId("eventId");
        savedEvent.setName(request.getName());
        savedEvent.setDescription(request.getDescription());
        savedEvent.setVenue(request.getVenue());
        savedEvent.setCity(request.getCity());
        savedEvent.setStartTime(request.getStartTime());
        savedEvent.setEndTime(request.getEndTime());
        savedEvent.setSaleStartTime(request.getSaleStartTime());
        savedEvent.setSaleEndTime(request.getSaleEndTime());
        savedEvent.setStatus(request.getStatus());
        savedEvent.setCreatedAt(Instant.now());
        savedEvent.setUpdatedAt(Instant.now());

        when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);

        EventResponse response = eventService.createEvent(request);

        assertNotNull(response);
        assertEquals("eventId", response.getId());
        assertEquals(request.getName(), response.getName());
        assertEquals(request.getDescription(), response.getDescription());
        assertEquals(request.getVenue(), response.getVenue());
        assertEquals(request.getCity(), response.getCity());
        assertEquals(request.getStartTime(), response.getStartTime());
        assertEquals(request.getEndTime(), response.getEndTime());
        assertEquals(request.getSaleStartTime(), response.getSaleStartTime());
        assertEquals(request.getSaleEndTime(), response.getSaleEndTime());
        assertEquals(request.getStatus(), response.getStatus());
    }

    @Test
    void testGetEventById() {
        String eventId = "eventId";
        Event event = new Event();
        event.setId(eventId);
        event.setName("Test Event");
        event.setDescription("Description");
        event.setVenue("Venue");
        event.setCity("City");
        event.setStartTime(Instant.now());
        event.setEndTime(Instant.now().plusSeconds(3600));
        event.setSaleStartTime(Instant.now());
        event.setSaleEndTime(Instant.now().plusSeconds(7200));
        event.setStatus("ON_SALE");
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        EventResponse response = eventService.getEventById(eventId);

        assertNotNull(response);
        assertEquals(eventId, response.getId());
        assertEquals("Test Event", response.getName());
        assertEquals("Description", response.getDescription());
        assertEquals("Venue", response.getVenue());
        assertEquals("City", response.getCity());
        assertEquals("ON_SALE", response.getStatus());
    }

    @Test
    void testGetAllEvents() {
        Event event1 = new Event();
        event1.setId("1");
        event1.setName("Event 1");
        event1.setStatus("ON_SALE");

        Event event2 = new Event();
        event2.setId("2");
        event2.setName("Event 2");
        event2.setStatus("DRAFT");

        when(eventRepository.findAll()).thenReturn(Arrays.asList(event1, event2));

        List<EventResponse> responses = eventService.getAllEvents();

        assertEquals(2, responses.size());
        assertEquals("Event 1", responses.get(0).getName());
        assertEquals("Event 2", responses.get(1).getName());
    }

    @Test
    void testGetEventsOnSale() {
        Instant now = Instant.now();
        Event event = new Event();
        event.setId("eventId");
        event.setName("On Sale Event");
        event.setSaleStartTime(now.minusSeconds(3600));
        event.setSaleEndTime(now.plusSeconds(3600));
        event.setStatus("ON_SALE");

        when(eventRepository.findBySaleStartTimeBeforeAndSaleEndTimeAfter(any(Instant.class), any(Instant.class)))
                .thenReturn(Arrays.asList(event));

        List<EventResponse> responses = eventService.getEventsOnSale();

        assertEquals(1, responses.size());
        assertEquals("On Sale Event", responses.get(0).getName());
    }

    @Test
    void testUpdateEvent() {
        String eventId = "eventId";
        Event existing = new Event();
        existing.setId(eventId);
        existing.setName("Old Name");
        existing.setDescription("Old Desc");
        existing.setVenue("Old Venue");
        existing.setCity("Old City");
        existing.setStartTime(Instant.now());
        existing.setEndTime(Instant.now().plusSeconds(3600));
        existing.setSaleStartTime(Instant.now());
        existing.setSaleEndTime(Instant.now().plusSeconds(7200));
        existing.setStatus("DRAFT");
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        EventRequest request = new EventRequest();
        request.setName("New Name");
        request.setDescription("New Desc");
        request.setVenue("New Venue");
        request.setCity("New City");
        request.setStartTime(Instant.now().plusSeconds(100));
        request.setEndTime(Instant.now().plusSeconds(3700));
        request.setSaleStartTime(Instant.now().plusSeconds(50));
        request.setSaleEndTime(Instant.now().plusSeconds(7250));
        request.setStatus("ON_SALE");

        Event updatedEvent = new Event();
        updatedEvent.setId(eventId);
        updatedEvent.setName(request.getName());
        updatedEvent.setDescription(request.getDescription());
        updatedEvent.setVenue(request.getVenue());
        updatedEvent.setCity(request.getCity());
        updatedEvent.setStartTime(request.getStartTime());
        updatedEvent.setEndTime(request.getEndTime());
        updatedEvent.setSaleStartTime(request.getSaleStartTime());
        updatedEvent.setSaleEndTime(request.getSaleEndTime());
        updatedEvent.setStatus(request.getStatus());
        updatedEvent.setCreatedAt(existing.getCreatedAt());
        updatedEvent.setUpdatedAt(Instant.now());

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(existing));
        when(eventRepository.save(any(Event.class))).thenReturn(updatedEvent);

        EventResponse response = eventService.updateEvent(eventId, request);

        assertNotNull(response);
        assertEquals(request.getName(), response.getName());
        assertEquals(request.getDescription(), response.getDescription());
        assertEquals(request.getVenue(), response.getVenue());
        assertEquals(request.getCity(), response.getCity());
        assertEquals(request.getStartTime(), response.getStartTime());
        assertEquals(request.getEndTime(), response.getEndTime());
        assertEquals(request.getSaleStartTime(), response.getSaleStartTime());
        assertEquals(request.getSaleEndTime(), response.getSaleEndTime());
        assertEquals(request.getStatus(), response.getStatus());
    }

    @Test
    void testUpdateEventWithoutStatusKeepsExistingStatus() {
        Event existing = new Event();
        existing.setId("eventId");
        existing.setStatus("ON_SALE");
        when(eventRepository.findById("eventId")).thenReturn(Optional.of(existing));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventRequest request = new EventRequest();
        request.setName("New Name");
        request.setVenue("Venue");
        request.setCity("City");
        request.setStartTime(Instant.now().plusSeconds(100));
        request.setEndTime(Instant.now().plusSeconds(3700));
        request.setSaleStartTime(Instant.now());
        request.setSaleEndTime(Instant.now().plusSeconds(50));

        EventResponse response = eventService.updateEvent("eventId", request);

        assertEquals("ON_SALE", response.getStatus());
    }

    @Test
    void testGetOrUpdateUnknownEventThrowsEventNotFound() {
        when(eventRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(EventNotFoundException.class, () -> eventService.getEventById("missing"));
        assertThrows(EventNotFoundException.class, () -> eventService.updateEvent("missing", new EventRequest()));
        verify(eventRepository, never()).save(any());
    }

    private Event eventWithStatus(String status) {
        Event event = new Event();
        event.setId("evt-1");
        event.setName("Delhi Music Festival");
        event.setStatus(status);
        event.setUpdatedAt(Instant.EPOCH);
        return event;
    }

    @Test
    void cancelSetsStatusCancelledForEveryCancellableStatus() {
        for (String status : List.of("DRAFT", "UPCOMING", "ON_SALE", "SOLD_OUT")) {
            reset(eventRepository);
            Event event = eventWithStatus(status);
            when(eventRepository.findById("evt-1")).thenReturn(Optional.of(event));
            when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

            EventResponse response = eventService.cancelEvent("evt-1");

            assertEquals("CANCELLED", response.getStatus(), "from " + status);
            assertTrue(response.getUpdatedAt().isAfter(Instant.EPOCH), "from " + status);
            verify(eventRepository).save(event);
        }
    }

    @Test
    void cancellingAnAlreadyCancelledEventIsIdempotent() {
        when(eventRepository.findById("evt-1")).thenReturn(Optional.of(eventWithStatus("CANCELLED")));

        EventResponse response = eventService.cancelEvent("evt-1");

        assertEquals("CANCELLED", response.getStatus());
        assertEquals(Instant.EPOCH, response.getUpdatedAt());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void completedEventCannotBeCancelled() {
        when(eventRepository.findById("evt-1")).thenReturn(Optional.of(eventWithStatus("COMPLETED")));

        assertThrows(EventStateConflictException.class, () -> eventService.cancelEvent("evt-1"));
        verify(eventRepository, never()).save(any());
    }

    @Test
    void cancellingUnknownEventThrowsNotFound() {
        when(eventRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(EventNotFoundException.class, () -> eventService.cancelEvent("missing"));
        verify(eventRepository, never()).save(any());
    }

    private EventRequest updateRequest(String status) {
        EventRequest request = new EventRequest();
        request.setName("Renamed");
        request.setVenue("Venue");
        request.setCity("City");
        request.setStartTime(Instant.now().plusSeconds(3600));
        request.setEndTime(Instant.now().plusSeconds(7200));
        request.setSaleStartTime(Instant.now());
        request.setSaleEndTime(Instant.now().plusSeconds(1800));
        request.setStatus(status);
        return request;
    }

    @Test
    void updateCannotReopenACancelledEvent() {
        when(eventRepository.findById("evt-1")).thenReturn(Optional.of(eventWithStatus("CANCELLED")));

        assertThrows(EventStateConflictException.class, () -> eventService.updateEvent("evt-1", updateRequest("ON_SALE")));
        verify(eventRepository, never()).save(any());
    }

    @Test
    void cancelledEventDetailsCanStillBeEditedWithoutChangingStatus() {
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        for (String status : new String[]{null, "CANCELLED"}) {
            when(eventRepository.findById("evt-1")).thenReturn(Optional.of(eventWithStatus("CANCELLED")));

            EventResponse response = eventService.updateEvent("evt-1", updateRequest(status));

            assertEquals("Renamed", response.getName());
            assertEquals("CANCELLED", response.getStatus());
        }
    }

    @Test
    void testDeleteEvent() {
        String eventId = "eventId";
        doNothing().when(eventRepository).deleteById(eventId);

        eventService.deleteEvent(eventId);

        verify(eventRepository, times(1)).deleteById(eventId);
    }
}