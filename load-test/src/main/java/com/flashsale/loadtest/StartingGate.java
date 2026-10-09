package com.flashsale.loadtest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

/**
 * Runs callers on a fixed number of threads. With {@code threads == callers} every caller waits behind one gate
 * and all start at the same instant: 5,000 buyers means 5,000 simultaneous purchase attempts.
 */
final class StartingGate {
    private StartingGate() {
    }

    static <T> List<T> releaseAll(int callers, int threads, IntFunction<T> caller) {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch gate = new CountDownLatch(1);
        try {
            List<Future<T>> calls = new ArrayList<>(callers);
            for (int index = 0; index < callers; index++) {
                int callerIndex = index;
                calls.add(pool.submit(() -> {
                    gate.await();
                    return caller.apply(callerIndex);
                }));
            }
            gate.countDown();

            List<T> results = new ArrayList<>(callers);
            for (Future<T> call : calls) {
                results.add(call.get());
            }
            return results;
        } catch (ExecutionException exception) {
            throw new IllegalStateException("A simulated caller crashed", exception.getCause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for callers", interrupted);
        } finally {
            pool.shutdownNow();
        }
    }
}
