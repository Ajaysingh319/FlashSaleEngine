package com.flashsale.reservation.service;

import com.flashsale.reservation.config.ReportingReads;
import com.flashsale.reservation.document.Reservation;
import com.flashsale.reservation.dto.PageResponse;
import com.flashsale.reservation.dto.ReservationResponse;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AdminReservationQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final ReportingReads reportingReads = mock(ReportingReads.class);
    private final AdminReservationQueryService service =
            new AdminReservationQueryService(reportingReads, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(reportingReads.mongo()).thenReturn(mongo);
    }

    private static Reservation reservation() {
        Reservation reservation = new Reservation();
        reservation.setId("res-1");
        reservation.setStatus("ACTIVE");
        return reservation;
    }

    @Test
    void listsUnexpiredActiveReservationsOfAnEventSoonestToExpireFirst() {
        when(mongo.find(any(Query.class), eq(Reservation.class))).thenReturn(Collections.nCopies(10, reservation()));
        when(mongo.count(any(Query.class), eq(Reservation.class))).thenReturn(25L);

        PageResponse<ReservationResponse> page = service.findActiveReservations("evt-1", 1, 10);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(Reservation.class));
        Document filter = query.getValue().getQueryObject();
        assertEquals("ACTIVE", filter.get("status"));
        assertEquals(new Document("$gt", NOW), filter.get("expiresAt"));
        assertEquals("evt-1", filter.get("eventId"));
        assertEquals(new Document("expiresAt", 1), query.getValue().getSortObject());
        assertEquals(10, query.getValue().getSkip());
        assertEquals("res-1", page.content().get(0).getReservationId());
        assertEquals(25, page.totalElements());
        assertEquals(3, page.totalPages());
    }

    @Test
    void withoutAnEventFilterListsAllEvents() {
        when(mongo.find(any(Query.class), eq(Reservation.class))).thenReturn(List.of());

        service.findActiveReservations(null, 0, 20);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(Reservation.class));
        assertFalse(query.getValue().getQueryObject().containsKey("eventId"));
    }

    @Test
    void pageSizeIsBounded() {
        for (int[] invalid : new int[][]{{-1, 20}, {0, 0}, {0, 101}}) {
            assertThrows(IllegalArgumentException.class, () -> service.findActiveReservations(null, invalid[0], invalid[1]));
        }
        verifyNoInteractions(mongo);
    }
}
