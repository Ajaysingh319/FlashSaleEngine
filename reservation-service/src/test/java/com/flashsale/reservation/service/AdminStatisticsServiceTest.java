package com.flashsale.reservation.service;

import com.flashsale.reservation.config.ReportingReads;
import com.flashsale.reservation.document.Inventory;
import com.flashsale.reservation.document.Reservation;
import com.flashsale.reservation.dto.EventStatisticsResponse;
import com.flashsale.reservation.dto.InventoryTotals;
import com.flashsale.reservation.dto.ReservationTotals;
import com.flashsale.reservation.dto.StatisticsResponse;
import com.flashsale.reservation.exception.EventNotFoundException;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AdminStatisticsServiceTest {
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final ReportingReads reportingReads = mock(ReportingReads.class);
    private final AdminStatisticsService service = new AdminStatisticsService(reportingReads);

    @BeforeEach
    void setUp() {
        when(reportingReads.mongo()).thenReturn(mongo);
        when(mongo.aggregate(any(Aggregation.class), eq(Reservation.class), eq(AdminStatisticsService.StatusGroup.class)))
                .thenReturn(new AggregationResults<>(List.of(
                        new AdminStatisticsService.StatusGroup("CONFIRMED", 50, 100),
                        new AdminStatisticsService.StatusGroup("EXPIRED", 3, 4)), new Document()));
        when(mongo.find(argThat(isQueryFor("status")), eq(Reservation.class)))
                .thenReturn(List.of(confirmed("4999.00"), confirmed("9998.00"), confirmed(null)));
    }

    private static org.mockito.ArgumentMatcher<Query> isQueryFor(String field) {
        return query -> query != null && query.getQueryObject().containsKey(field);
    }

    private static Reservation confirmed(String amount) {
        Reservation reservation = new Reservation();
        reservation.setAmount(amount == null ? null : new BigDecimal(amount));
        return reservation;
    }

    private static Inventory inventory(String eventId, String ticketTypeId, int total, int available, int reserved, int sold) {
        return new Inventory(ticketTypeId, ticketTypeId, eventId, total, available, reserved, sold, Instant.EPOCH);
    }

    @Test
    void eventStatisticsReportInventoryPerTicketTypeReservationsAndRevenue() {
        when(mongo.find(argThat(isQueryFor("eventId")), eq(Inventory.class))).thenReturn(List.of(
                inventory("evt-1", "tt-gold", 60, 0, 0, 60), inventory("evt-1", "tt-vip", 40, 0, 0, 40)));

        EventStatisticsResponse statistics = service.eventStatistics("evt-1");

        assertEquals(new InventoryTotals(100, 0, 0, 100), statistics.inventory());
        assertEquals(0, statistics.inventory().oversold());
        assertTrue(statistics.inventory().consistent());
        assertEquals(List.of("tt-gold", "tt-vip"),
                statistics.ticketTypes().stream().map(type -> type.ticketTypeId()).toList());
        assertEquals(new ReservationTotals(50, 100), statistics.reservationsByStatus().get("CONFIRMED"));
        assertEquals(new ReservationTotals(0, 0), statistics.reservationsByStatus().get("ACTIVE"), "unused statuses show zero");
        assertEquals(new BigDecimal("14997.00"), statistics.confirmedRevenue());
    }

    @Test
    void eventWithoutInventoryIsNotFound() {
        when(mongo.find(any(Query.class), eq(Inventory.class))).thenReturn(List.of());

        assertThrows(EventNotFoundException.class, () -> service.eventStatistics("missing"));
    }

    @Test
    void platformStatisticsSumEveryEvent() {
        when(mongo.findAll(Inventory.class)).thenReturn(List.of(
                inventory("evt-2", "tt-1", 10, 10, 0, 0), inventory("evt-1", "tt-2", 100, 0, 5, 95),
                inventory("evt-1", "tt-3", 20, 20, 0, 0)));

        StatisticsResponse statistics = service.statistics();

        assertEquals(new InventoryTotals(130, 30, 5, 95), statistics.inventory());
        assertEquals(List.of("evt-1", "evt-2"), statistics.events().stream().map(event -> event.eventId()).toList());
        assertEquals(new InventoryTotals(120, 20, 5, 95), statistics.events().get(0).inventory());
        assertEquals(new BigDecimal("14997.00"), statistics.confirmedRevenue());
    }

    @Test
    void oversoldOrInconsistentInventoryIsFlagged() {
        InventoryTotals oversold = new InventoryTotals(100, 0, 0, 101);
        InventoryTotals leaked = new InventoryTotals(100, 1, 0, 98);

        assertEquals(1, oversold.oversold());
        assertFalse(oversold.consistent());
        assertEquals(0, leaked.oversold());
        assertFalse(leaked.consistent());
        assertFalse(new InventoryTotals(100, -1, 1, 100).consistent());
    }
}
