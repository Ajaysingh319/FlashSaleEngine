package com.flashsale.catalog.service;

import com.flashsale.catalog.document.Event;
import com.flashsale.catalog.dto.EventRequest;
import com.flashsale.catalog.dto.EventResponse;
import com.flashsale.catalog.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    public EventResponse createEvent(EventRequest request) {
        Event event = new Event();
        event.setName(request.getName());
        event.setDescription(request.getDescription());
        event.setVenue(request.getVenue());
        event.setCity(request.getCity());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setSaleStartTime(request.getSaleStartTime());
        event.setSaleEndTime(request.getSaleEndTime());
        event.setStatus(request.getStatus() != null ? request.getStatus() : "DRAFT");
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());

        Event saved = eventRepository.save(event);
        return mapToResponse(saved);
    }

    public EventResponse getEventById(String id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        return mapToResponse(event);
    }

    public List<EventResponse> getAllEvents() {
        return eventRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<EventResponse> getEventsOnSale() {
        Instant now = Instant.now();
        return eventRepository.findBySaleStartTimeBeforeAndSaleEndTimeAfter(now, now).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public EventResponse updateEvent(String id, EventRequest request) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        event.setName(request.getName());
        event.setDescription(request.getDescription());
        event.setVenue(request.getVenue());
        event.setCity(request.getCity());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setSaleStartTime(request.getSaleStartTime());
        event.setSaleEndTime(request.getSaleEndTime());
        event.setStatus(request.getStatus());
        event.setUpdatedAt(Instant.now());

        Event saved = eventRepository.save(event);
        return mapToResponse(saved);
    }

    public void deleteEvent(String id) {
        eventRepository.deleteById(id);
    }

    private EventResponse mapToResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getVenue(),
                event.getCity(),
                event.getStartTime(),
                event.getEndTime(),
                event.getSaleStartTime(),
                event.getSaleEndTime(),
                event.getStatus(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}