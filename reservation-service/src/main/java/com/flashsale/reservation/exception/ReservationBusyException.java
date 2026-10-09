package com.flashsale.reservation.exception;

/** Another request holds the ticket-type or user lock right now; nothing changed, so the client may simply retry. */
public class ReservationBusyException extends RuntimeException {
    public ReservationBusyException() { super("Tickets are in high demand; please retry"); }
}
