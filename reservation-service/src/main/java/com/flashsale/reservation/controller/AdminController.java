package com.flashsale.reservation.controller;

import com.flashsale.reservation.dto.EventStatisticsResponse;
import com.flashsale.reservation.dto.PageResponse;
import com.flashsale.reservation.dto.ReservationResponse;
import com.flashsale.reservation.dto.StatisticsResponse;
import com.flashsale.reservation.service.AdminReservationQueryService;
import com.flashsale.reservation.service.AdminStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN-only monitoring of reservations and sales (PRD 6.14, 16); access is enforced in ReservationSecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminReservationQueryService adminReservationQueryService;
    private final AdminStatisticsService adminStatisticsService;

    @GetMapping("/reservations/active")
    public PageResponse<ReservationResponse> getActiveReservations(
            @RequestParam(name = "eventId", required = false) String eventId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return adminReservationQueryService.findActiveReservations(eventId, page, size);
    }

    @GetMapping("/events/{eventId}/statistics")
    public EventStatisticsResponse getEventStatistics(@PathVariable("eventId") String eventId) {
        return adminStatisticsService.eventStatistics(eventId);
    }

    @GetMapping("/statistics")
    public StatisticsResponse getStatistics() {
        return adminStatisticsService.statistics();
    }
}
