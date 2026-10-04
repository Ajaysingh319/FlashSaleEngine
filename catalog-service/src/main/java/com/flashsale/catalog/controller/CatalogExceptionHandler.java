package com.flashsale.catalog.controller;

import com.flashsale.catalog.exception.EventNotFoundException;
import com.flashsale.catalog.exception.EventStateConflictException;
import com.flashsale.catalog.exception.InventoryProvisioningException;
import com.flashsale.catalog.exception.TicketTypeConflictException;
import com.flashsale.catalog.exception.TicketTypeNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.stream.Collectors;

/** Maps catalog errors to the standard error response (TDD section 49). */
@RestControllerAdvice
public class CatalogExceptionHandler {

    @ExceptionHandler(EventStateConflictException.class)
    ResponseEntity<ErrorResponse> handleEventStateConflict(EventStateConflictException exception) {
        return error(HttpStatus.CONFLICT, "EVENT_STATE_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(TicketTypeNotFoundException.class)
    ResponseEntity<ErrorResponse> handleTicketTypeNotFound(TicketTypeNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "TICKET_TYPE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(TicketTypeConflictException.class)
    ResponseEntity<ErrorResponse> handleTicketTypeConflict(TicketTypeConflictException exception) {
        return error(HttpStatus.CONFLICT, "TICKET_TYPE_CONFLICT", exception.getMessage());
    }

    /** Ticket type stays PENDING; repeating the same setup request completes it once Reservation is reachable. */
    @ExceptionHandler(InventoryProvisioningException.class)
    ResponseEntity<ErrorResponse> handleInventoryProvisioning(InventoryProvisioningException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "Inventory setup could not be completed; the ticket type is pending. Retry the same request.");
    }

    @ExceptionHandler(EventNotFoundException.class)
    ResponseEntity<ErrorResponse> handleEventNotFound(EventNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", exception.getMessage());
    }

    /** Invalid request bodies (MethodArgumentNotValidException) and invalid query parameters. */
    @ExceptionHandler(BindException.class)
    ResponseEntity<ErrorResponse> handleBindException(BindException exception) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .map(CatalogExceptionHandler::describe)
                .sorted()
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid value for parameter '" + exception.getName() + "'");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Malformed request body");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    private static String describe(ObjectError error) {
        if (error instanceof FieldError fieldError) {
            if (fieldError.isBindingFailure()) {
                return fieldError.getField() + ": invalid value";
            }
            return fieldError.getField() + ": " + fieldError.getDefaultMessage();
        }
        return error.getDefaultMessage();
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(false, new ErrorDetail(code, message), Instant.now()));
    }

    record ErrorResponse(boolean success, ErrorDetail error, Instant timestamp) { }
    record ErrorDetail(String code, String message) { }
}
