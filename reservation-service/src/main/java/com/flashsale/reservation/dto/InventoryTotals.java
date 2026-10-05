package com.flashsale.reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashsale.reservation.document.Inventory;

/**
 * Ticket counts of one or more ticket types. The TDD 73 invariants (every count >= 0 and
 * total = available + reserved + sold) are what "no overselling" means; both are reported for the load test.
 */
public record InventoryTotals(long total, long available, long reserved, long sold) {
    public static final InventoryTotals EMPTY = new InventoryTotals(0, 0, 0, 0);

    public static InventoryTotals of(Inventory inventory) {
        return new InventoryTotals(count(inventory.getTotalQuantity()), count(inventory.getAvailableQuantity()),
                count(inventory.getReservedQuantity()), count(inventory.getSoldQuantity()));
    }

    public InventoryTotals plus(InventoryTotals other) {
        return new InventoryTotals(total + other.total, available + other.available,
                reserved + other.reserved, sold + other.sold);
    }

    /** Tickets sold beyond the total; must always be 0. */
    @JsonProperty("oversold")
    public long oversold() {
        return Math.max(0, sold - total);
    }

    @JsonProperty("consistent")
    public boolean consistent() {
        return available >= 0 && reserved >= 0 && sold >= 0 && available + reserved + sold == total;
    }

    private static long count(Integer value) {
        return value == null ? 0 : value;
    }
}
