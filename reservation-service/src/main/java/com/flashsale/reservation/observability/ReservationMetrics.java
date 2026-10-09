package com.flashsale.reservation.observability;

import com.flashsale.reservation.exception.InventoryUnavailableException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Reservation metrics of TDD 69: reservation.success.count, reservation.failure.count (tagged with the failure
 * reason, e.g. ReservationBusyException or InventoryUnavailableException), inventory.conflict.count (sold out)
 * and redis.lock.latency.
 */
@Component
public class ReservationMetrics {
    private final MeterRegistry registry;
    private final Counter successes;
    private final Counter inventoryConflicts;
    private final Timer lockLatency;

    public ReservationMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.successes = registry.counter("reservation.success.count");
        this.inventoryConflicts = registry.counter("inventory.conflict.count");
        this.lockLatency = Timer.builder("redis.lock.latency")
                .description("Time to acquire a Redis reservation lock")
                .register(registry);
    }

    /** Runs one reservation attempt and counts how it ended; the outcome or exception is passed on unchanged. */
    public <T> T recordReservation(Supplier<T> attempt) {
        try {
            T reservation = attempt.get();
            successes.increment();
            return reservation;
        } catch (RuntimeException failure) {
            if (failure instanceof InventoryUnavailableException) {
                inventoryConflicts.increment();
            }
            registry.counter("reservation.failure.count", "reason", failure.getClass().getSimpleName()).increment();
            throw failure;
        }
    }

    public <T> T timeLockAcquisition(Supplier<T> acquire) {
        return lockLatency.record(acquire);
    }
}
