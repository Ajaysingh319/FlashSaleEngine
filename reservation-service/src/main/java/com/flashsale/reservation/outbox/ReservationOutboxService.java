package com.flashsale.reservation.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.reservation.document.Reservation;
import com.flashsale.reservation.document.ReservationOutboxEvent;
import com.flashsale.reservation.repository.ReservationOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** Persists lifecycle events inside the caller's Mongo transaction. */
@Service
@RequiredArgsConstructor
public class ReservationOutboxService {
    private final ReservationOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void append(String topic, String eventType, Reservation reservation) {
        Instant timestamp = Instant.now();
        ReservationOutboxEvent event = new ReservationOutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setAggregateType("RESERVATION");
        event.setAggregateId(reservation.getId());
        event.setTopic(topic);
        event.setEventType(eventType);
        event.setPayload(payload(reservation, event.getEventId(), eventType, timestamp));
        event.setCreatedAt(timestamp);
        event.setPublishAttempts(0);
        outboxRepository.insert(event);
    }

    private String payload(Reservation reservation, String eventId, String eventType, Instant timestamp) {
        try {
            return objectMapper.writeValueAsString(new ReservationEventEnvelope(
                    eventId, eventType, timestamp, "RESERVATION", reservation.getId(),
                    new ReservationLifecyclePayload(reservation.getId(), reservation.getEventId(), reservation.getTicketTypeId(),
                            reservation.getUserId(), reservation.getQuantity(), reservation.getStatus(), reservation.getExpiresAt())));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize reservation lifecycle event", exception);
        }
    }

    public record ReservationEventEnvelope(
            String eventId, String eventType, Instant timestamp, String aggregateType, String aggregateId,
            ReservationLifecyclePayload payload) { }

    public record ReservationLifecyclePayload(
            String reservationId, String eventId, String ticketTypeId, String userId,
            Integer quantity, String status, Instant expiresAt) { }
}
