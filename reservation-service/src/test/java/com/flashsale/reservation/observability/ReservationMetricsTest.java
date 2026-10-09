package com.flashsale.reservation.observability;

import com.flashsale.reservation.exception.InventoryUnavailableException;
import com.flashsale.reservation.exception.ReservationBusyException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReservationMetricsTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ReservationMetrics metrics = new ReservationMetrics(registry);

    private double count(String name, String... tags) {
        return registry.get(name).tags(tags).counter().count();
    }

    @Test
    void successIsCountedAndTheResultReturned() {
        assertEquals("res-1", metrics.recordReservation(() -> "res-1"));

        assertEquals(1, count("reservation.success.count"));
    }

    @Test
    void failuresAreCountedByReasonAndSoldOutIsAnInventoryConflict() {
        assertThrows(InventoryUnavailableException.class, () -> metrics.recordReservation(() -> {
            throw new InventoryUnavailableException();
        }));
        assertThrows(ReservationBusyException.class, () -> metrics.recordReservation(() -> {
            throw new ReservationBusyException();
        }));

        assertEquals(1, count("reservation.failure.count", "reason", "InventoryUnavailableException"));
        assertEquals(1, count("reservation.failure.count", "reason", "ReservationBusyException"));
        assertEquals(1, count("inventory.conflict.count"));
        assertEquals(0, count("reservation.success.count"));
    }

    @Test
    void lockAcquisitionIsTimed() {
        assertTrue(metrics.timeLockAcquisition(() -> true));

        assertEquals(1, registry.get("redis.lock.latency").timer().count());
    }
}
