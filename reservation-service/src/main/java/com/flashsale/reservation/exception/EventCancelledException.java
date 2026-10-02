package com.flashsale.reservation.exception;

/** BR-010: cancelled events cannot accept new reservations. */
public class EventCancelledException extends RuntimeException {
    public EventCancelledException(String eventId) { super("Event " + eventId + " is cancelled"); }
}
