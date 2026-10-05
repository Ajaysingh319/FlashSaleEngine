package com.flashsale.reservation.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** GET /api/v1/admin/events/{eventId}/statistics: one event's inventory, reservations and confirmed revenue. */
public record EventStatisticsResponse(String eventId,
                                      InventoryTotals inventory,
                                      List<TicketTypeStatistics> ticketTypes,
                                      Map<String, ReservationTotals> reservationsByStatus,
                                      BigDecimal confirmedRevenue) { }
