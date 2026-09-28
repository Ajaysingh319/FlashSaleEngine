package com.flashsale.catalog.controller;

import com.flashsale.catalog.dto.EventRequest;
import com.flashsale.catalog.dto.EventResponse;
import com.flashsale.catalog.dto.TicketTypeRequest;
import com.flashsale.catalog.dto.TicketTypeResponse;
import com.flashsale.catalog.service.EventService;
import com.flashsale.catalog.service.TicketTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogController {

    private final EventService eventService;
    private final TicketTypeService ticketTypeService;

    // Events
    @PostMapping("/events")
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody EventRequest request) {
        return ResponseEntity.ok(eventService.createEvent(request));
    }

    @GetMapping("/events")
    public ResponseEntity<List<EventResponse>> getAllEvents() {
        return ResponseEntity.ok(eventService.getAllEvents());
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<EventResponse> getEventById(@PathVariable String id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    @PutMapping("/events/{id}")
    public ResponseEntity<EventResponse> updateEvent(@PathVariable String id, @Valid @RequestBody EventRequest request) {
        return ResponseEntity.ok(eventService.updateEvent(id, request));
    }

    @DeleteMapping("/events/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable String id) {
        eventService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/events/on-sale")
    public ResponseEntity<List<EventResponse>> getEventsOnSale() {
        return ResponseEntity.ok(eventService.getEventsOnSale());
    }

    // Ticket Types
    @PostMapping("/ticket-types")
    public ResponseEntity<TicketTypeResponse> createTicketType(@Valid @RequestBody TicketTypeRequest request) {
        return ResponseEntity.ok(ticketTypeService.createTicketType(request));
    }

    @GetMapping("/ticket-types/{id}")
    public ResponseEntity<TicketTypeResponse> getTicketTypeById(@PathVariable String id) {
        return ResponseEntity.ok(ticketTypeService.getTicketTypeById(id));
    }

    @GetMapping("/events/{eventId}/ticket-types")
    public ResponseEntity<List<TicketTypeResponse>> getTicketTypesByEventId(@PathVariable String eventId) {
        return ResponseEntity.ok(ticketTypeService.getTicketTypesByEventId(eventId));
    }

    @PutMapping("/ticket-types/{id}")
    public ResponseEntity<TicketTypeResponse> updateTicketType(@PathVariable String id, @Valid @RequestBody TicketTypeRequest request) {
        return ResponseEntity.ok(ticketTypeService.updateTicketType(id, request));
    }

    @DeleteMapping("/ticket-types/{id}")
    public ResponseEntity<Void> deleteTicketType(@PathVariable String id) {
        ticketTypeService.deleteTicketType(id);
        return ResponseEntity.noContent().build();
    }
}