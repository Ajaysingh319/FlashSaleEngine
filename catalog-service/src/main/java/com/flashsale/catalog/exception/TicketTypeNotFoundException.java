package com.flashsale.catalog.exception;

/** Ticket type does not exist. */
public class TicketTypeNotFoundException extends RuntimeException {
    public TicketTypeNotFoundException(String message) {
        super(message);
    }

    public TicketTypeNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
