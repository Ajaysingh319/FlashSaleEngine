package com.flashsale.loadtest;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One customer racing for a ticket: reserve, then order, then pay (PRD 7). Like a real client it retries a
 * RESERVATION_BUSY answer with the same Idempotency-Key after a short jittered back-off, and stops on sold out.
 */
final class SimulatedBuyer {
    enum Outcome {
        /** Reserved, ordered and payment accepted. */
        PURCHASED,
        /** Told the tickets are gone (409): the correct answer for everyone after the 100th buyer. */
        SOLD_OUT,
        /** Still busy after every retry, or rate limited (429): no ticket, but nothing went wrong. */
        GAVE_UP,
        /** A 5xx, no response, or an unexpected answer. */
        FAILED
    }

    private static final int MAX_RESERVE_ATTEMPTS = 8;

    private final FlashSaleApi api;
    private final String token;
    private final FlashSaleApi.Sale sale;

    SimulatedBuyer(FlashSaleApi api, String token, FlashSaleApi.Sale sale) {
        this.api = api;
        this.token = token;
        this.sale = sale;
    }

    Outcome buy() {
        ApiResponse reservation = reserve();
        if (!reservation.isSuccess()) {
            return outcomeOfRejected(reservation);
        }
        ApiResponse order = api.createOrder(token, reservation.field("reservationId"), newKey());
        if (!order.isSuccess()) {
            return Outcome.FAILED;
        }
        return api.pay(token, order.field("orderId"), newKey()).isSuccess() ? Outcome.PURCHASED : Outcome.FAILED;
    }

    private ApiResponse reserve() {
        String idempotencyKey = newKey();
        ApiResponse response = api.reserve(token, sale, idempotencyKey);
        for (int attempt = 1; attempt < MAX_RESERVE_ATTEMPTS && response.isBusy(); attempt++) {
            backOff(attempt);
            response = api.reserve(token, sale, idempotencyKey);
        }
        return response;
    }

    private static Outcome outcomeOfRejected(ApiResponse reservation) {
        if (reservation.status() == 409) {
            return Outcome.SOLD_OUT;
        }
        return reservation.isBusy() || reservation.status() == 429 ? Outcome.GAVE_UP : Outcome.FAILED;
    }

    /** Exponential back-off with full jitter, capped at one second, so retries spread out instead of colliding. */
    private static void backOff(int attempt) {
        long ceilingMillis = Math.min(1_000, 20L << attempt);
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(1, ceilingMillis + 1));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off", interrupted);
        }
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }
}
