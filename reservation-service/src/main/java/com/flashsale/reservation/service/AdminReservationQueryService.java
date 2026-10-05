package com.flashsale.reservation.service;

import com.flashsale.reservation.config.ReportingReads;
import com.flashsale.reservation.document.Reservation;
import com.flashsale.reservation.document.ReservationStatus;
import com.flashsale.reservation.dto.PageResponse;
import com.flashsale.reservation.dto.ReservationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Reservations currently holding tickets (PRD 6.14, GET /api/v1/admin/reservations/active): ACTIVE and not yet
 * past expiry, soonest to expire first. Always paged and index-backed, and read from secondaries.
 */
@Service
@RequiredArgsConstructor
public class AdminReservationQueryService {
    static final int MAX_PAGE_SIZE = 100;

    private final ReportingReads reportingReads;
    private final Clock clock;

    public PageResponse<ReservationResponse> findActiveReservations(String eventId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        Query query = Query.query(where("status").is(ReservationStatus.ACTIVE.name()).and("expiresAt").gt(clock.instant()));
        if (eventId != null && !eventId.isBlank()) {
            query.addCriteria(where("eventId").is(eventId));
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "expiresAt"));

        MongoTemplate mongo = reportingReads.mongo();
        List<ReservationResponse> reservations = mongo.find(Query.of(query).with(pageable), Reservation.class).stream()
                .map(ReservationResponse::from)
                .toList();
        return PageResponse.from(PageableExecutionUtils.getPage(reservations, pageable,
                () -> mongo.count(query, Reservation.class)));
    }
}
