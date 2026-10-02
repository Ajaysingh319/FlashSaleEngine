package com.flashsale.reservation.exception;

/** Event eligibility could not be verified with Catalog Service; reservations fail closed. */
public class CatalogUnavailableException extends RuntimeException {
    public CatalogUnavailableException(String message) { super(message); }
    public CatalogUnavailableException(String message, Throwable cause) { super(message, cause); }
}
