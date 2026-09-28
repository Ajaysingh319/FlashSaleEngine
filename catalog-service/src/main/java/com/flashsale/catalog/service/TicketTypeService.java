package com.flashsale.catalog.service;

import com.flashsale.catalog.document.Event;
import com.flashsale.catalog.document.TicketType;
import com.flashsale.catalog.dto.TicketTypeRequest;
import com.flashsale.catalog.dto.TicketTypeResponse;
import com.flashsale.catalog.repository.EventRepository;
import com.flashsale.catalog.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketTypeService {

    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;

    public TicketTypeResponse createTicketType(TicketTypeRequest request) {
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new RuntimeException("Event not found"));

        TicketType ticketType = new TicketType();
        ticketType.setName(request.getName());
        ticketType.setPrice(request.getPrice());
        ticketType.setTotalQuantity(request.getTotalQuantity());
        ticketType.setAvailableQuantity(request.getAvailableQuantity());
        ticketType.setReservedQuantity(request.getReservedQuantity());
        ticketType.setSoldQuantity(request.getSoldQuantity());
        ticketType.setEvent(event);
        ticketType.setCreatedAt(Instant.now());
        ticketType.setUpdatedAt(Instant.now());

        TicketType saved = ticketTypeRepository.save(ticketType);
        return mapToResponse(saved);
    }

    public TicketTypeResponse getTicketTypeById(String id) {
        TicketType ticketType = ticketTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket type not found"));
        return mapToResponse(ticketType);
    }

    public List<TicketTypeResponse> getTicketTypesByEventId(String eventId) {
        return ticketTypeRepository.findByEventId(eventId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TicketTypeResponse updateTicketType(String id, TicketTypeRequest request) {
        TicketType ticketType = ticketTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket type not found"));
        ticketType.setName(request.getName());
        ticketType.setPrice(request.getPrice());
        ticketType.setTotalQuantity(request.getTotalQuantity());
        ticketType.setAvailableQuantity(request.getAvailableQuantity());
        ticketType.setReservedQuantity(request.getReservedQuantity());
        ticketType.setSoldQuantity(request.getSoldQuantity());

        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new RuntimeException("Event not found"));
        ticketType.setEvent(event);
        ticketType.setUpdatedAt(Instant.now());

        TicketType saved = ticketTypeRepository.save(ticketType);
        return mapToResponse(saved);
    }

    public void deleteTicketType(String id) {
        ticketTypeRepository.deleteById(id);
    }

    private TicketTypeResponse mapToResponse(TicketType ticketType) {
        return new TicketTypeResponse(
                ticketType.getId(),
                ticketType.getName(),
                ticketType.getPrice(),
                ticketType.getTotalQuantity(),
                ticketType.getAvailableQuantity(),
                ticketType.getReservedQuantity(),
                ticketType.getSoldQuantity(),
                ticketType.getEvent().getId(),
                ticketType.getCreatedAt(),
                ticketType.getUpdatedAt()
        );
    }
}