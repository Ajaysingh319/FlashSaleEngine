package com.flashsale.reservation.exception;

public class EventNotFoundException extends RuntimeException {
    public EventNotFoundException(String eventId) { super("Event not found: " + eventId); }
}
