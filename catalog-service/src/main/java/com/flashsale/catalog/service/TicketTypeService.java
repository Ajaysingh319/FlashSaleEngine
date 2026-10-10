package com.flashsale.catalog.service;

import com.flashsale.catalog.client.ReservationInventoryClient;
import com.flashsale.catalog.document.Event;
import com.flashsale.catalog.document.InventoryStatus;
import com.flashsale.catalog.document.TicketType;
import com.flashsale.catalog.dto.TicketTypeRequest;
import com.flashsale.catalog.dto.TicketTypeResponse;
import com.flashsale.catalog.exception.EventNotFoundException;
import com.flashsale.catalog.exception.TicketTypeConflictException;
import com.flashsale.catalog.exception.TicketTypeNotFoundException;
import com.flashsale.catalog.repository.EventRepository;
import com.flashsale.catalog.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketTypeService {

    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final ReservationInventoryClient reservationInventoryClient;

    /**
     * Creates a ticket type and initializes its inventory in Reservation Service. The ticket type is saved
     * as PENDING first and becomes READY only after Reservation confirms the inventory, so a failure is never
     * reported as success. (eventId, name) identifies the setup: repeating the same request completes a
     * PENDING setup or returns the READY one without adding stock; different values are a conflict.
     */
    public TicketTypeResponse createTicketType(String eventId, TicketTypeRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventId));

        TicketType ticketType = ticketTypeRepository.findByEventIdAndName(eventId, request.getName())
                .orElse(null);
        if (ticketType == null) {
            ticketType = insertPending(request, event);
        }
        ensureSameSetup(ticketType, request);

        if (ticketType.getInventoryStatus() != InventoryStatus.READY) {
            reservationInventoryClient.initializeInventory(
                    ticketType.getEventId(), ticketType.getId(), ticketType.getTotalQuantity());
            ticketType.setInventoryStatus(InventoryStatus.READY);
            ticketType.setUpdatedAt(Instant.now());
            ticketType = ticketTypeRepository.save(ticketType);
        }
        return mapToResponse(ticketType);
    }

    private TicketType insertPending(TicketTypeRequest request, Event event) {
        Instant now = Instant.now();
        TicketType ticketType = new TicketType();
        ticketType.setName(request.getName());
        ticketType.setPrice(request.getPrice());
        ticketType.setTotalQuantity(request.getTotalQuantity());
        ticketType.setInventoryStatus(InventoryStatus.PENDING);
        ticketType.setEventId(event.getId());
        ticketType.setEvent(event);
        ticketType.setCreatedAt(now);
        ticketType.setUpdatedAt(now);
        try {
            return ticketTypeRepository.insert(ticketType);
        } catch (DuplicateKeyException exception) {
            // A concurrent request created the same (eventId, name) first; continue with that record.
            return ticketTypeRepository.findByEventIdAndName(event.getId(), request.getName())
                    .orElseThrow(() -> exception);
        }
    }

    private void ensureSameSetup(TicketType existing, TicketTypeRequest request) {
        if (!Objects.equals(existing.getTotalQuantity(), request.getTotalQuantity())
                || existing.getPrice() == null || Double.compare(existing.getPrice(), request.getPrice()) != 0) {
            throw new TicketTypeConflictException("Ticket type '" + request.getName() + "' already exists for event "
                    + existing.getEventId() + " with a different price or total quantity");
        }
    }

    public TicketTypeResponse getTicketTypeById(String id) {
        return mapToResponse(findTicketType(id));
    }

    /** Lists only ticket types whose inventory is set up in Reservation Service, i.e. that can be reserved. */
    public List<TicketTypeResponse> getTicketTypesByEventId(String eventId) {
        return ticketTypeRepository.findByEventIdAndInventoryStatus(eventId, InventoryStatus.READY).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Updates name and price. The event is the one the ticket type was created under, and totalQuantity is fixed
     * once set up because Reservation Service owns the inventory and has no stock adjustment API.
     */
    public TicketTypeResponse updateTicketType(String id, TicketTypeRequest request) {
        TicketType ticketType = findTicketType(id);
        if (!Objects.equals(ticketType.getTotalQuantity(), request.getTotalQuantity())) {
            throw new TicketTypeConflictException("totalQuantity of ticket type " + id
                    + " cannot be changed after setup; inventory is owned by Reservation Service");
        }
        ticketType.setName(request.getName());
        ticketType.setPrice(request.getPrice());
        ticketType.setUpdatedAt(Instant.now());
        try {
            return mapToResponse(ticketTypeRepository.save(ticketType));
        } catch (DuplicateKeyException exception) {
            throw new TicketTypeConflictException("Ticket type '" + request.getName() + "' already exists for event "
                    + ticketType.getEventId(), exception);
        }
    }

    public void deleteTicketType(String id) {
        ticketTypeRepository.deleteById(id);
    }

    private TicketType findTicketType(String id) {
        return ticketTypeRepository.findById(id)
                .orElseThrow(() -> new TicketTypeNotFoundException("Ticket type not found: " + id));
    }

    private TicketTypeResponse mapToResponse(TicketType ticketType) {
        return new TicketTypeResponse(
                ticketType.getId(),
                ticketType.getName(),
                ticketType.getPrice(),
                ticketType.getTotalQuantity(),
                ticketType.getInventoryStatus() != null ? ticketType.getInventoryStatus().name() : null,
                ticketType.getEventId(),
                ticketType.getCreatedAt(),
                ticketType.getUpdatedAt()
        );
    }
}
