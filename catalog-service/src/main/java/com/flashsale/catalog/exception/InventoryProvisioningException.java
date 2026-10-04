package com.flashsale.catalog.exception;

/** Inventory could not be initialized in Reservation Service; the ticket type stays PENDING and the same request can be retried. */
public class InventoryProvisioningException extends RuntimeException {
    public InventoryProvisioningException(String message) {
        super(message);
    }

    public InventoryProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
