package com.flashsale.payment.kafka;

/** A consumed event that can never be processed (malformed or incomplete). It is dead-lettered without retries. */
public class InvalidEventException extends RuntimeException {
    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
