package com.flashsale.reservation.exception;

public class InventoryNotFoundException extends RuntimeException {
    public InventoryNotFoundException(String ticketTypeId) { super("No inventory for ticket type " + ticketTypeId); }
}
