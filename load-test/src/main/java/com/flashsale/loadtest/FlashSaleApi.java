package com.flashsale.loadtest;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** The flash-sale endpoints the simulation uses (PRD 16), expressed in domain terms over {@link GatewayClient}. */
final class FlashSaleApi {
    private final GatewayClient gateway;

    FlashSaleApi(GatewayClient gateway) {
        this.gateway = gateway;
    }

    String login(String email, String password) {
        return accessToken(gateway.post("login", "/api/v1/auth/login", null, null,
                Map.of("email", email, "password", password)));
    }

    String register(String email, String password) {
        return accessToken(gateway.post("register", "/api/v1/auth/register", null, null,
                Map.of("email", email, "password", password)));
    }

    /** An event on sale right now with one ticket type of {@code tickets} seats; Catalog provisions the inventory. */
    Sale openSale(String adminToken, String runId, int tickets) {
        Instant now = Instant.now();
        ApiResponse event = required(gateway.post("setup", "/api/v1/events", adminToken, null, Map.of(
                "name", "Load test " + runId,
                "venue", "Simulation Arena",
                "city", "Bengaluru",
                "startTime", now.plus(Duration.ofDays(7)).toString(),
                "endTime", now.plus(Duration.ofDays(7)).plus(Duration.ofHours(3)).toString(),
                "saleStartTime", now.minus(Duration.ofMinutes(1)).toString(),
                "saleEndTime", now.plus(Duration.ofHours(2)).toString(),
                "status", "ON_SALE")), "create event");
        String eventId = event.field("id");
        ApiResponse ticketType = required(gateway.post("setup", "/api/v1/events/" + eventId + "/ticket-types", adminToken,
                null, Map.of("name", "General", "price", 4999.0, "totalQuantity", tickets)), "create ticket type");
        return new Sale(eventId, ticketType.field("id"));
    }

    ApiResponse reserve(String token, Sale sale, String idempotencyKey) {
        return gateway.post("reserve", "/api/v1/reservations", token, idempotencyKey,
                Map.of("eventId", sale.eventId(), "ticketTypeId", sale.ticketTypeId(), "quantity", 1));
    }

    ApiResponse createOrder(String token, String reservationId, String idempotencyKey) {
        return gateway.post("order", "/api/v1/orders", token, idempotencyKey, Map.of("reservationId", reservationId));
    }

    ApiResponse pay(String token, String orderId, String idempotencyKey) {
        return gateway.post("pay", "/api/v1/orders/" + orderId + "/payment", token, idempotencyKey,
                Map.of("paymentMethod", "MOCK_CARD"));
    }

    /** Reservation's authoritative inventory for the event, including the oversold/consistent checks. */
    JsonNode inventory(String adminToken, Sale sale) {
        return required(gateway.get("statistics", "/api/v1/admin/events/" + sale.eventId() + "/statistics", adminToken),
                "read event statistics").body().path("inventory");
    }

    private static String accessToken(ApiResponse response) {
        return required(response, "authenticate").field("accessToken");
    }

    private static ApiResponse required(ApiResponse response, String action) {
        if (!response.isSuccess()) {
            throw new IllegalStateException("Could not " + action + ": HTTP " + response.status() + " " + response.body());
        }
        return response;
    }

    record Sale(String eventId, String ticketTypeId) { }
}
