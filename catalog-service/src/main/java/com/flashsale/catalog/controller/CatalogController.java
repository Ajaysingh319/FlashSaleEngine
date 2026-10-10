package com.flashsale.catalog.controller;

import com.flashsale.catalog.dto.EventRequest;
import com.flashsale.catalog.dto.EventResponse;
import com.flashsale.catalog.dto.EventSearchRequest;
import com.flashsale.catalog.dto.TicketTypeRequest;
import com.flashsale.catalog.dto.TicketTypeResponse;
import com.flashsale.catalog.service.EventService;
import com.flashsale.catalog.service.TicketTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

    /**
     * Lists, searches, filters, sorts and paginates events. The body stays a JSON array; pagination
     * metadata is returned in the X-Total-Count and X-Total-Pages headers.
     */
    @GetMapping("/events")
    public ResponseEntity<List<EventResponse>> searchEvents(@Valid @ModelAttribute EventSearchRequest searchRequest) {
        Page<EventResponse> page = eventService.searchEvents(searchRequest);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .header("X-Total-Pages", String.valueOf(page.getTotalPages()))
                .body(page.getContent());
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<EventResponse> getEventById(@PathVariable("id") String id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    @PutMapping("/events/{id}")
    public ResponseEntity<EventResponse> updateEvent(@PathVariable("id") String id, @Valid @RequestBody EventRequest request) {
        return ResponseEntity.ok(eventService.updateEvent(id, request));
    }

    /** PRD 6.14 / 16: ADMIN only (CatalogSecurityConfig and the API Gateway). */
    @PostMapping("/events/{id}/cancel")
    public ResponseEntity<EventResponse> cancelEvent(@PathVariable("id") String id) {
        return ResponseEntity.ok(eventService.cancelEvent(id));
    }

    @DeleteMapping("/events/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable("id") String id) {
        eventService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/events/on-sale")
    public ResponseEntity<List<EventResponse>> getEventsOnSale() {
        return ResponseEntity.ok(eventService.getEventsOnSale());
    }

    // Ticket Types
    /** PRD 16: a ticket type is created under its event. */
    @PostMapping("/events/{eventId}/ticket-types")
    public ResponseEntity<TicketTypeResponse> createTicketType(@PathVariable("eventId") String eventId,
                                                               @Valid @RequestBody TicketTypeRequest request) {
        return ResponseEntity.ok(ticketTypeService.createTicketType(eventId, request));
    }

    @GetMapping("/ticket-types/{id}")
    public ResponseEntity<TicketTypeResponse> getTicketTypeById(@PathVariable("id") String id) {
        return ResponseEntity.ok(ticketTypeService.getTicketTypeById(id));
    }

    @GetMapping("/events/{eventId}/ticket-types")
    public ResponseEntity<List<TicketTypeResponse>> getTicketTypesByEventId(@PathVariable("eventId") String eventId) {
        return ResponseEntity.ok(ticketTypeService.getTicketTypesByEventId(eventId));
    }

    @PutMapping("/ticket-types/{id}")
    public ResponseEntity<TicketTypeResponse> updateTicketType(@PathVariable("id") String id, @Valid @RequestBody TicketTypeRequest request) {
        return ResponseEntity.ok(ticketTypeService.updateTicketType(id, request));
    }

    @DeleteMapping("/ticket-types/{id}")
    public ResponseEntity<Void> deleteTicketType(@PathVariable("id") String id) {
        ticketTypeService.deleteTicketType(id);
        return ResponseEntity.noContent().build();
    }
}