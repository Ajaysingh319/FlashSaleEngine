package com.flashsale.loadtest;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** Per-operation statistics for every request the simulation sends (TDD 78). */
final class Metrics {
    private final Map<String, OperationStats> operations = new ConcurrentHashMap<>();

    void record(String operation, ApiResponse response, long latencyNanos) {
        operations.computeIfAbsent(operation, name -> new OperationStats()).record(response, latencyNanos);
    }

    /** Operations sorted by name. */
    Map<String, OperationStats> byOperation() {
        return new TreeMap<>(operations);
    }
}
