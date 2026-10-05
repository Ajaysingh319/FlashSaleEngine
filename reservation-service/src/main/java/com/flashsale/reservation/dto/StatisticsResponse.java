package com.flashsale.reservation.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** GET /api/v1/admin/statistics: platform-wide totals plus a per-event inventory summary. */
public record StatisticsResponse(InventoryTotals inventory,
                                 Map<String, ReservationTotals> reservationsByStatus,
                                 BigDecimal confirmedRevenue,
                                 List<EventSummary> events) { }
