package com.flashsale.reservation.exception;

public class ReservationExpiredException extends RuntimeException {
    public ReservationExpiredException() { super("Reservation has expired"); }
}
