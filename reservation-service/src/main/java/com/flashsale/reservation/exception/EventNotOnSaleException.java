package com.flashsale.reservation.exception;

/** BR-006: reservations are accepted only while saleStartTime <= now <= saleEndTime. */
public class EventNotOnSaleException extends RuntimeException {
    public EventNotOnSaleException(String message) { super(message); }
}
