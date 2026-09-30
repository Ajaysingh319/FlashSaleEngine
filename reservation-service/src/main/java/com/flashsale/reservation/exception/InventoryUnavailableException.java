package com.flashsale.reservation.exception;

public class InventoryUnavailableException extends RuntimeException {
    public InventoryUnavailableException() { super("Requested inventory is no longer available"); }
}
