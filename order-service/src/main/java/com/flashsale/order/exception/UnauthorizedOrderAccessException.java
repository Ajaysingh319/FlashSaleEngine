package com.flashsale.order.exception;

public class UnauthorizedOrderAccessException extends RuntimeException {
    public UnauthorizedOrderAccessException(String message) {
        super(message);
    }
}