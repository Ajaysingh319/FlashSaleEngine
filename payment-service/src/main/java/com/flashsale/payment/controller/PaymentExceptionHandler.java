package com.flashsale.payment.controller;

import com.flashsale.payment.exception.PaymentAccessDeniedException;
import com.flashsale.payment.exception.PaymentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/** Standard error envelope (TDD 49). */
@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(PaymentNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(PaymentAccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleAccessDenied(PaymentAccessDeniedException exception) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", exception.getMessage());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(false, new ErrorDetail(code, message), Instant.now()));
    }

    record ErrorResponse(boolean success, ErrorDetail error, Instant timestamp) { }
    record ErrorDetail(String code, String message) { }
}
