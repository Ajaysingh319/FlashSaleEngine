package com.flashsale.reservation.controller;

import com.flashsale.reservation.exception.IdempotencyConflictException;
import com.flashsale.reservation.exception.InventoryUnavailableException;
import com.flashsale.reservation.exception.InvalidReservationStateException;
import com.flashsale.reservation.exception.PurchaseLimitExceededException;
import com.flashsale.reservation.exception.ReservationExpiredException;
import com.flashsale.reservation.exception.ReservationNotFoundException;
import com.flashsale.reservation.exception.ReservationOwnershipException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ReservationExceptionHandler {
    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException exception) {
        return error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", exception.getMessage());
    }

    @ExceptionHandler(InventoryUnavailableException.class)
    ResponseEntity<ErrorResponse> handleInventoryUnavailable(InventoryUnavailableException exception) {
        return error(HttpStatus.CONFLICT, "INVENTORY_UNAVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(PurchaseLimitExceededException.class)
    ResponseEntity<ErrorResponse> handlePurchaseLimit(PurchaseLimitExceededException exception) {
        return error(HttpStatus.CONFLICT, "PURCHASE_LIMIT_EXCEEDED", exception.getMessage());
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(ReservationNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ReservationOwnershipException.class)
    ResponseEntity<ErrorResponse> handleOwnership(ReservationOwnershipException exception) {
        return error(HttpStatus.FORBIDDEN, "RESERVATION_NOT_OWNED", exception.getMessage());
    }

    @ExceptionHandler(ReservationExpiredException.class)
    ResponseEntity<ErrorResponse> handleExpired(ReservationExpiredException exception) {
        return error(HttpStatus.CONFLICT, "RESERVATION_EXPIRED", exception.getMessage());
    }

    @ExceptionHandler(InvalidReservationStateException.class)
    ResponseEntity<ErrorResponse> handleInvalidState(InvalidReservationStateException exception) {
        return error(HttpStatus.CONFLICT, "INVALID_RESERVATION_STATE", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(false, new ErrorDetail(code, message), Instant.now()));
    }

    record ErrorResponse(boolean success, ErrorDetail error, Instant timestamp) { }
    record ErrorDetail(String code, String message) { }
}
