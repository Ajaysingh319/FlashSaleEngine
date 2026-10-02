package com.flashsale.reservation.service;

import com.flashsale.reservation.dto.CatalogEventResponse;
import com.flashsale.reservation.exception.CatalogUnavailableException;
import com.flashsale.reservation.exception.EventCancelledException;
import com.flashsale.reservation.exception.EventNotFoundException;
import com.flashsale.reservation.exception.EventNotOnSaleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventSaleEligibilityValidatorTest {

    private static final Instant SALE_START = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant SALE_END = Instant.parse("2026-11-01T12:00:00Z");

    private final CatalogServiceClient catalogClient = mock(CatalogServiceClient.class);

    private EventSaleEligibilityValidator validatorAt(Instant now) {
        return new EventSaleEligibilityValidator(catalogClient, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void catalogReturns(String status) {
        when(catalogClient.findEvent("event-1"))
                .thenReturn(Optional.of(new CatalogEventResponse("event-1", status, SALE_START, SALE_END)));
    }

    @Test
    void saleWindowIsInclusiveAtBothEnds() {
        catalogReturns("ON_SALE");

        assertDoesNotThrow(() -> validatorAt(SALE_START).assertReservable("event-1"));
        assertDoesNotThrow(() -> validatorAt(SALE_START.plusSeconds(60)).assertReservable("event-1"));
        assertDoesNotThrow(() -> validatorAt(SALE_END).assertReservable("event-1"));
    }

    @Test
    void oneMillisecondBeforeSaleStartIsRejected() {
        catalogReturns("ON_SALE");

        EventNotOnSaleException exception = assertThrows(EventNotOnSaleException.class,
                () -> validatorAt(SALE_START.minusMillis(1)).assertReservable("event-1"));
        assertEquals("Ticket sales for event event-1 have not started", exception.getMessage());
    }

    @Test
    void oneMillisecondAfterSaleEndIsRejected() {
        catalogReturns("ON_SALE");

        EventNotOnSaleException exception = assertThrows(EventNotOnSaleException.class,
                () -> validatorAt(SALE_END.plusMillis(1)).assertReservable("event-1"));
        assertEquals("Ticket sales for event event-1 have ended", exception.getMessage());
    }

    @Test
    void cancelledEventIsRejectedInsideSaleWindow() {
        catalogReturns("CANCELLED");

        assertThrows(EventCancelledException.class, () -> validatorAt(SALE_START.plusSeconds(60)).assertReservable("event-1"));
    }

    @Test
    void cancellationTakesPrecedenceOverClosedSaleWindow() {
        catalogReturns("CANCELLED");

        assertThrows(EventCancelledException.class, () -> validatorAt(SALE_END.plusSeconds(60)).assertReservable("event-1"));
    }

    /** Only CANCELLED is blocked by status (BR-010); other statuses rely on the sale window and inventory. */
    @ParameterizedTest
    @ValueSource(strings = {"UPCOMING", "ON_SALE", "SOLD_OUT"})
    void nonCancelledStatusesInsideWindowAreAllowed(String status) {
        catalogReturns(status);

        assertDoesNotThrow(() -> validatorAt(SALE_START.plusSeconds(60)).assertReservable("event-1"));
    }

    @Test
    void missingEventIsRejected() {
        when(catalogClient.findEvent("event-1")).thenReturn(Optional.empty());

        assertThrows(EventNotFoundException.class, () -> validatorAt(SALE_START).assertReservable("event-1"));
    }

    @Test
    void catalogFailurePropagatesSoCallerFailsClosed() {
        when(catalogClient.findEvent("event-1")).thenThrow(new CatalogUnavailableException("down"));

        assertThrows(CatalogUnavailableException.class, () -> validatorAt(SALE_START).assertReservable("event-1"));
    }
}
