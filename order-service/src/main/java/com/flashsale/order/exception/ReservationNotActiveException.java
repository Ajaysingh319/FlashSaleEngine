package com.flashsale.order.exception;

public class ReservationNotActiveException extends RuntimeException {
    public ReservationNotActiveException(String message) {
        super(message);
    }
}