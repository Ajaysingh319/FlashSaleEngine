package com.flashsale.reservation.service;

import com.flashsale.reservation.config.ReportingReads;
import com.flashsale.reservation.document.Inventory;
import com.flashsale.reservation.document.Reservation;
import com.flashsale.reservation.document.ReservationStatus;
import com.flashsale.reservation.dto.EventStatisticsResponse;
import com.flashsale.reservation.dto.EventSummary;
import com.flashsale.reservation.dto.InventoryTotals;
import com.flashsale.reservation.dto.ReservationTotals;
import com.flashsale.reservation.dto.StatisticsResponse;
import com.flashsale.reservation.dto.TicketTypeStatistics;
import com.flashsale.reservation.exception.EventNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Sales statistics for administrators (PRD 6.14, 16), computed from Reservation's authoritative inventory. They
 * are the "Overselling = 0" proof of a load test (TDD 73, 78): every ticket type must stay consistent and nothing
 * may be oversold. All reads go to secondaries (ReportingReads), away from the reservation hot path.
 */
@Service
@RequiredArgsConstructor
public class AdminStatisticsService {
    private final ReportingReads reportingReads;

    public EventStatisticsResponse eventStatistics(String eventId) {
        List<Inventory> inventory = mongo().find(
                Query.query(where("eventId").is(eventId)).with(Sort.by("ticketTypeId")), Inventory.class);
        if (inventory.isEmpty()) {
            throw new EventNotFoundException(eventId);
        }
        List<TicketTypeStatistics> ticketTypes = inventory.stream()
                .map(item -> new TicketTypeStatistics(item.getTicketTypeId(), InventoryTotals.of(item)))
                .toList();
        InventoryTotals totals = ticketTypes.stream()
                .map(TicketTypeStatistics::inventory)
                .reduce(InventoryTotals.EMPTY, InventoryTotals::plus);
        return new EventStatisticsResponse(eventId, totals, ticketTypes,
                reservationsByStatus(eventId), confirmedRevenue(eventId));
    }

    public StatisticsResponse statistics() {
        Map<String, InventoryTotals> byEvent = mongo().findAll(Inventory.class).stream()
                .collect(Collectors.groupingBy(Inventory::getEventId, TreeMap::new,
                        Collectors.reducing(InventoryTotals.EMPTY, InventoryTotals::of, InventoryTotals::plus)));
        List<EventSummary> events = byEvent.entrySet().stream()
                .map(entry -> new EventSummary(entry.getKey(), entry.getValue()))
                .toList();
        InventoryTotals totals = byEvent.values().stream().reduce(InventoryTotals.EMPTY, InventoryTotals::plus);
        return new StatisticsResponse(totals, reservationsByStatus(null), confirmedRevenue(null), events);
    }

    /** Reservation and ticket counts per status, counted inside MongoDB; every status is present, zero if unused. */
    private Map<String, ReservationTotals> reservationsByStatus(String eventId) {
        List<AggregationOperation> stages = new ArrayList<>();
        if (eventId != null) {
            stages.add(Aggregation.match(where("eventId").is(eventId)));
        }
        stages.add(Aggregation.group("status").count().as("reservations").sum("quantity").as("tickets"));

        Map<String, ReservationTotals> byStatus = new TreeMap<>();
        for (ReservationStatus status : ReservationStatus.values()) {
            byStatus.put(status.name(), new ReservationTotals(0, 0));
        }
        mongo().aggregate(Aggregation.newAggregation(stages), Reservation.class, StatusGroup.class)
                .forEach(group -> byStatus.put(group.id(), new ReservationTotals(group.reservations(), group.tickets())));
        return byStatus;
    }

    /**
     * Sum of CONFIRMED reservation amounts. Amounts are stored as exact decimal strings, so they are added in Java;
     * only the amount field of confirmed reservations is read, and those are bounded by the tickets sold.
     */
    private BigDecimal confirmedRevenue(String eventId) {
        Query confirmed = Query.query(where("status").is(ReservationStatus.CONFIRMED.name()));
        if (eventId != null) {
            confirmed.addCriteria(where("eventId").is(eventId));
        }
        confirmed.fields().include("amount");
        return mongo().find(confirmed, Reservation.class).stream()
                .map(Reservation::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private MongoTemplate mongo() {
        return reportingReads.mongo();
    }

    /** One $group result: _id is the reservation status. */
    record StatusGroup(String id, long reservations, long tickets) { }
}
