package com.flashsale.order.exception;

public class ReservationServiceUnavailableException extends RuntimeException {
    public ReservationServiceUnavailableException(String message) {
        super(message);
    }
}