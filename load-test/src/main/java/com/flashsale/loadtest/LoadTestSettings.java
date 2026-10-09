package com.flashsale.loadtest;

import java.net.URI;
import java.util.UUID;

/**
 * Scenario settings, read from environment variables so no credentials live in code. The defaults are the headline
 * scenario of PRD 11: 5,000 concurrent buyers for 100 tickets. Other TDD 77 scenarios only change BUYERS/TICKETS.
 */
record LoadTestSettings(URI gateway, int buyers, int tickets, String adminEmail, String adminPassword,
                        String buyerPassword, String runId) {

    static LoadTestSettings fromEnvironment() {
        String runId = UUID.randomUUID().toString().substring(0, 8);
        return new LoadTestSettings(
                URI.create(optional("GATEWAY_URL", "http://localhost:8080")),
                Integer.parseInt(optional("BUYERS", "5000")),
                Integer.parseInt(optional("TICKETS", "100")),
                required("ADMIN_EMAIL"),
                required("ADMIN_PASSWORD"),
                // Throwaway password for this run's simulated accounts; never a real credential.
                "Load-" + UUID.randomUUID(),
                runId);
    }

    /** A unique address per run, so every run registers fresh buyers. */
    String buyerEmail(int buyer) {
        return "buyer-" + runId + "-" + buyer + "@loadtest.example";
    }

    private static String optional(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set (the Auth Service admin account)");
        }
        return value;
    }
}
