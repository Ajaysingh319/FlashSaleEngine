package com.flashsale.loadtest;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * End-to-end flash-sale simulation through the API Gateway (PRD 11-12, TDD 77-78):
 * <ol>
 *   <li>setup: an admin opens a sale with TICKETS seats and BUYERS accounts are registered;</li>
 *   <li>burst: every buyer starts at the same instant and tries to reserve, order and pay;</li>
 *   <li>settle: wait for asynchronous payments to move reserved tickets to sold;</li>
 *   <li>report: outcomes, latency percentiles and the oversold = 0 check. Exit code 1 if the run fails.</li>
 * </ol>
 * Run against a started stack: {@code mvn -q compile exec:java} with ADMIN_EMAIL and ADMIN_PASSWORD set.
 */
public final class FlashSaleLoadTest {
    /** Registration hashes passwords with BCrypt, so it is spread over a bounded pool before the burst starts. */
    private static final int REGISTRATION_THREADS = 50;
    private static final Duration SETTLE_TIMEOUT = Duration.ofMinutes(2);

    private final LoadTestSettings settings;
    private final Metrics metrics = new Metrics();
    private final FlashSaleApi api;

    private FlashSaleLoadTest(LoadTestSettings settings) {
        this.settings = settings;
        this.api = new FlashSaleApi(new GatewayClient(settings.gateway(), metrics));
    }

    public static void main(String[] args) {
        LoadTestReport report = new FlashSaleLoadTest(LoadTestSettings.fromEnvironment()).run();
        report.print();
        System.exit(report.passed() ? 0 : 1);
    }

    private LoadTestReport run() {
        String adminToken = api.login(settings.adminEmail(), settings.adminPassword());
        FlashSaleApi.Sale sale = api.openSale(adminToken, settings.runId(), settings.tickets());
        System.out.printf("Sale %s open with %d tickets; registering %d buyers...%n",
                sale.eventId(), settings.tickets(), settings.buyers());
        List<String> buyerTokens = StartingGate.releaseAll(settings.buyers(), REGISTRATION_THREADS,
                buyer -> api.register(settings.buyerEmail(buyer), settings.buyerPassword()));

        System.out.printf("Releasing %d buyers at once...%n", settings.buyers());
        Instant burstStart = Instant.now();
        List<SimulatedBuyer.Outcome> outcomes = StartingGate.releaseAll(settings.buyers(), settings.buyers(),
                buyer -> new SimulatedBuyer(api, buyerTokens.get(buyer), sale).buy());
        Duration burst = Duration.between(burstStart, Instant.now());

        return new LoadTestReport(settings, outcomes, burst, metrics, settledInventory(adminToken, sale));
    }

    /** Polls Reservation's inventory until no ticket is still held by an unpaid reservation, or the timeout passes. */
    private JsonNode settledInventory(String adminToken, FlashSaleApi.Sale sale) {
        Instant deadline = Instant.now().plus(SETTLE_TIMEOUT);
        JsonNode inventory = api.inventory(adminToken, sale);
        while (inventory.path("reserved").asLong() > 0 && Instant.now().isBefore(deadline)) {
            pause();
            inventory = api.inventory(adminToken, sale);
        }
        return inventory;
    }

    private static void pause() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for payments to settle", interrupted);
        }
    }
}
