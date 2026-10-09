package com.flashsale.reservation.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntFunction;

/**
 * Releases many callers at the same instant (TDD 73). Every caller has its own thread and waits behind one gate,
 * so "5,000 concurrent buyers" means 5,000 simultaneous attempts rather than a queue worked off by a small pool.
 */
final class StartingGate {
    private static final long TIMEOUT_MINUTES = 5;

    private StartingGate() {
    }

    /** Runs {@code caller} once per index, all starting together, and returns every result in index order. */
    static <T> List<T> releaseAll(int callers, IntFunction<T> caller) {
        ExecutorService threads = Executors.newFixedThreadPool(callers);
        CountDownLatch gate = new CountDownLatch(1);
        try {
            List<Future<T>> attempts = new ArrayList<>(callers);
            for (int index = 0; index < callers; index++) {
                int callerIndex = index;
                attempts.add(threads.submit(() -> {
                    gate.await();
                    return caller.apply(callerIndex);
                }));
            }
            gate.countDown();

            List<T> results = new ArrayList<>(callers);
            for (Future<T> attempt : attempts) {
                results.add(attempt.get(TIMEOUT_MINUTES, TimeUnit.MINUTES));
            }
            return results;
        } catch (ExecutionException exception) {
            throw new AssertionError("A concurrent caller failed unexpectedly", exception.getCause());
        } catch (InterruptedException | TimeoutException exception) {
            throw new AssertionError("Concurrent callers did not finish in " + TIMEOUT_MINUTES + " minutes", exception);
        } finally {
            threads.shutdownNow();
        }
    }
}
