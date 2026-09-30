package com.flashsale.order.exception;

/** Reservation cannot perform the requested Order-driven lifecycle transition. */
public class ReservationLifecycleConflictException extends RuntimeException {
    public ReservationLifecycleConflictException(String message) { super(message); }
}
