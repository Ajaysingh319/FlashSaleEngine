package com.flashsale.loadtest;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The result of one run (PRD 12, TDD 78): buyer outcomes, throughput, latency percentiles, error rate and the
 * final inventory. The run passes only if nothing was oversold, the inventory invariants hold and no buyer
 * hit a server error.
 */
final class LoadTestReport {
    /** Requests made during the burst; registration and sale setup are reported but not counted as the burst. */
    private static final Set<String> BURST_OPERATIONS = Set.of("reserve", "order", "pay");

    private final LoadTestSettings settings;
    private final Map<SimulatedBuyer.Outcome, Long> outcomes = new EnumMap<>(SimulatedBuyer.Outcome.class);
    private final Duration burst;
    private final Metrics metrics;
    private final JsonNode inventory;

    LoadTestReport(LoadTestSettings settings, List<SimulatedBuyer.Outcome> buyerOutcomes, Duration burst,
                   Metrics metrics, JsonNode inventory) {
        this.settings = settings;
        this.burst = burst;
        this.metrics = metrics;
        this.inventory = inventory;
        for (SimulatedBuyer.Outcome outcome : SimulatedBuyer.Outcome.values()) {
            outcomes.put(outcome, 0L);
        }
        buyerOutcomes.forEach(outcome -> outcomes.merge(outcome, 1L, Long::sum));
    }

    boolean passed() {
        return inventory.path("oversold").asLong(-1) == 0
                && inventory.path("consistent").asBoolean(false)
                && outcomes.get(SimulatedBuyer.Outcome.PURCHASED) <= settings.tickets()
                && outcomes.get(SimulatedBuyer.Outcome.FAILED) == 0;
    }

    void print() {
        System.out.printf("%nFlash sale: %d concurrent buyers for %d tickets via %s%n",
                settings.buyers(), settings.tickets(), settings.gateway());
        System.out.printf("Burst took %.2f s, %.0f requests/s, error rate %.2f%%%n",
                burst.toMillis() / 1000.0, burstRequests() / Math.max(burst.toMillis() / 1000.0, 0.001), errorRatePercent());

        System.out.println("\nBuyers");
        outcomes.forEach((outcome, count) -> System.out.printf("  %-10s %6d%n", outcome, count));
        System.out.printf("  Reservation success rate %.2f%%, payment success rate %.2f%%%n",
                reservationSuccessRatePercent(), paymentSuccessRatePercent());

        System.out.println("\nOperation   requests     P50 ms     P95 ms     P99 ms   statuses");
        metrics.byOperation().forEach((name, stats) -> System.out.printf("  %-8s %9d %10.1f %10.1f %10.1f   %s%n",
                name, stats.requests(), stats.percentileMillis(50), stats.percentileMillis(95),
                stats.percentileMillis(99), stats.statusCounts()));

        System.out.printf("%nInventory  total %s, available %s, reserved %s, sold %s, oversold %s, consistent %s%n",
                inventory.path("total"), inventory.path("available"), inventory.path("reserved"),
                inventory.path("sold"), inventory.path("oversold"), inventory.path("consistent"));
        System.out.println(passed() ? "\nRESULT: PASS (oversold = 0)" : "\nRESULT: FAIL");
    }

    private long burstRequests() {
        return burstStats().mapToLong(OperationStats::requests).sum();
    }

    private double errorRatePercent() {
        long requests = burstRequests();
        return requests == 0 ? 0 : 100.0 * burstStats().mapToLong(OperationStats::errors).sum() / requests;
    }

    /** Buyers who got a reservation; retries of a busy request do not count as extra attempts. */
    private double reservationSuccessRatePercent() {
        OperationStats reservations = metrics.byOperation().get("reserve");
        long reserved = reservations == null ? 0 : reservations.statusCounts().getOrDefault(200, 0L);
        return 100.0 * reserved / settings.buyers();
    }

    /** Payment initiations accepted (202) out of all attempted. */
    private double paymentSuccessRatePercent() {
        OperationStats payments = metrics.byOperation().get("pay");
        if (payments == null || payments.requests() == 0) {
            return 0;
        }
        return 100.0 * payments.statusCounts().getOrDefault(202, 0L) / payments.requests();
    }

    private Stream<OperationStats> burstStats() {
        return metrics.byOperation().entrySet().stream()
                .filter(entry -> BURST_OPERATIONS.contains(entry.getKey()))
                .map(Map.Entry::getValue);
    }
}
