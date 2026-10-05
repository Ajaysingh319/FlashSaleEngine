package com.flashsale.order.controller;

import com.flashsale.order.dto.ApiError;
import com.flashsale.order.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class OrderExceptionHandler {
    @ExceptionHandler({OrderNotFoundException.class, ReservationNotFoundException.class})
    ResponseEntity<ApiError> notFound(RuntimeException exception) { return error(HttpStatus.NOT_FOUND, exception); }

    @ExceptionHandler({UnauthorizedOrderAccessException.class, ReservationOwnershipException.class})
    ResponseEntity<ApiError> forbidden(RuntimeException exception) { return error(HttpStatus.FORBIDDEN, exception); }

    @ExceptionHandler({IdempotencyConflictException.class, ReservationAlreadyUsedException.class, InvalidOrderStateException.class})
    ResponseEntity<ApiError> conflict(RuntimeException exception) { return error(HttpStatus.CONFLICT, exception); }

    @ExceptionHandler(ReservationExpiredException.class)
    ResponseEntity<ApiError> expired(RuntimeException exception) { return error(HttpStatus.GONE, exception); }

    @ExceptionHandler({ReservationNotActiveException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> badRequest(RuntimeException exception) { return error(HttpStatus.BAD_REQUEST, exception); }

    @ExceptionHandler({MissingRequestHeaderException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ApiError> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError(Instant.now(), HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(), exception.getMessage()));
    }

    @ExceptionHandler(ReservationServiceUnavailableException.class)
    ResponseEntity<ApiError> dependencyUnavailable(RuntimeException exception) { return error(HttpStatus.BAD_GATEWAY, exception); }

    private ResponseEntity<ApiError> error(HttpStatus status, RuntimeException exception) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), exception.getMessage()));
    }
}
