package com.flashsale.catalog.exception;

/** The requested change is not allowed in the event's current status (e.g. cancelling a COMPLETED event). */
public class EventStateConflictException extends RuntimeException {
    public EventStateConflictException(String message) {
        super(message);
    }
}
