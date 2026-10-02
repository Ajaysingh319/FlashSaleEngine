package com.flashsale.reservation.service;

import com.flashsale.reservation.dto.CatalogEventResponse;
import com.flashsale.reservation.exception.EventCancelledException;
import com.flashsale.reservation.exception.EventNotFoundException;
import com.flashsale.reservation.exception.EventNotOnSaleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Checks that an event can accept new reservations (TDD 21 step 4): it must exist, must not be
 * CANCELLED (BR-010), and the current time must be inside the sale window, inclusive at both ends:
 * saleStartTime <= now <= saleEndTime (BR-006).
 */
@Component
@RequiredArgsConstructor
public class EventSaleEligibilityValidator {
    private static final String CANCELLED = "CANCELLED";

    private final CatalogServiceClient catalogServiceClient;
    private final Clock clock;

    public void assertReservable(String eventId) {
        CatalogEventResponse event = catalogServiceClient.findEvent(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        if (CANCELLED.equals(event.getStatus())) {
            throw new EventCancelledException(eventId);
        }
        Instant now = clock.instant();
        if (now.isBefore(event.getSaleStartTime())) {
            throw new EventNotOnSaleException("Ticket sales for event " + eventId + " have not started");
        }
        if (now.isAfter(event.getSaleEndTime())) {
            throw new EventNotOnSaleException("Ticket sales for event " + eventId + " have ended");
        }
    }
}
