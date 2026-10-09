package com.flashsale.loadtest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Latencies and HTTP statuses of one operation (e.g. "reserve"), safe to record from many threads. */
final class OperationStats {
    private final List<Long> latenciesNanos = new ArrayList<>();
    private final Map<Integer, Long> statusCounts = new TreeMap<>();
    private long errors;

    synchronized void record(ApiResponse response, long latencyNanos) {
        latenciesNanos.add(latencyNanos);
        statusCounts.merge(response.status(), 1L, Long::sum);
        if (response.isError()) {
            errors++;
        }
    }

    synchronized long requests() {
        return latenciesNanos.size();
    }

    synchronized long errors() {
        return errors;
    }

    synchronized Map<Integer, Long> statusCounts() {
        return new TreeMap<>(statusCounts);
    }

    /** Nearest-rank percentile in milliseconds, e.g. {@code percentileMillis(95)} for P95. */
    synchronized double percentileMillis(double percentile) {
        if (latenciesNanos.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(latenciesNanos);
        Collections.sort(sorted);
        int rank = (int) Math.ceil(percentile / 100 * sorted.size());
        return sorted.get(Math.max(rank - 1, 0)) / 1_000_000.0;
    }
}
