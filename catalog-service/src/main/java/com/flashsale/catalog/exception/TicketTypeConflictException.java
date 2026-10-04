package com.flashsale.catalog.exception;

/** Ticket type setup conflicts with an existing ticket type (same event and name, different values) or with an immutable field. */
public class TicketTypeConflictException extends RuntimeException {
    public TicketTypeConflictException(String message) {
        super(message);
    }

    public TicketTypeConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
