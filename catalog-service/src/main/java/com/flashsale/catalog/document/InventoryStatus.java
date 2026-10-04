package com.flashsale.catalog.document;

/** Whether a ticket type's inventory has been initialized in Reservation Service (the inventory authority). */
public enum InventoryStatus {
    /** Saved in Catalog; inventory setup in Reservation has not yet succeeded. Retrying the same setup completes it. */
    PENDING,
    /** Inventory exists in Reservation Service; the ticket type is reservable. */
    READY
}
