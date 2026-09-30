package com.flashsale.reservation.exception;

public class PurchaseLimitExceededException extends RuntimeException {
    public PurchaseLimitExceededException(int limit) { super("Purchase limit of " + limit + " tickets per event exceeded"); }
}
