package com.flashsale.reservation.exception;

/** Inventory for the ticket type already exists with a different setup (event or total quantity). */
public class InventoryConflictException extends RuntimeException {
    public InventoryConflictException(String message) { super(message); }
}
