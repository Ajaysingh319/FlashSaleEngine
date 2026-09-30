package com.flashsale.reservation.exception;

public class ReservationOwnershipException extends RuntimeException {
    public ReservationOwnershipException() { super("Reservation does not belong to the authenticated user"); }
}
