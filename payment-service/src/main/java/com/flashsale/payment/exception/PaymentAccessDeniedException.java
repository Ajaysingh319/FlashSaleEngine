package com.flashsale.payment.exception;

public class PaymentAccessDeniedException extends RuntimeException {
    public PaymentAccessDeniedException() { super("Payment belongs to another user"); }
}
