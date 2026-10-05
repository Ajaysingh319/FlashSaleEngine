package com.flashsale.auth.controller;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.flashsale.auth.exception.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/** Maps invalid request bodies (400) and a missing account (404) to the shared error envelope. Other errors keep their existing handling. */
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return badRequest(message.isEmpty() ? "Request validation failed" : message);
    }

    /** Missing, non-JSON or wrongly typed bodies; the parser's own message is not returned because it names internal classes. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        if (exception.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            String field = mismatch.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                    .collect(Collectors.joining("."));
            return badRequest("Invalid value for field '" + field + "'");
        }
        return badRequest("Request body is missing or is not valid JSON");
    }

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", exception.getMessage());
    }

    private ResponseEntity<ErrorResponse> badRequest(String message) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(false, new ErrorDetail(code, message), Instant.now()));
    }

    record ErrorResponse(boolean success, ErrorDetail error, Instant timestamp) { }
    record ErrorDetail(String code, String message) { }
}
