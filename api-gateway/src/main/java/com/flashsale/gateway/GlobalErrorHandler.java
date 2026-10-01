package com.flashsale.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global error handler to return JSON error responses.
 */
@RestControllerAdvice
public class GlobalErrorHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = "Internal server error";
        }
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), message));
    }

    private static class ErrorResponse {
        private final int status;
        private final String message;

        public ErrorResponse(int status, String message) {
            this.status = status;
            this.message = message;
        }

        public int getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }
}