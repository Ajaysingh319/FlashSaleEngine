package com.flashsale.reservation.controller;

import com.flashsale.reservation.exception.CatalogUnavailableException;
import com.flashsale.reservation.exception.EventCancelledException;
import com.flashsale.reservation.exception.EventNotFoundException;
import com.flashsale.reservation.exception.EventNotOnSaleException;
import com.flashsale.reservation.exception.IdempotencyConflictException;
import com.flashsale.reservation.exception.InventoryConflictException;
import com.flashsale.reservation.exception.InventoryNotFoundException;
import com.flashsale.reservation.exception.InventoryUnavailableException;
import com.flashsale.reservation.exception.ReservationBusyException;
import com.flashsale.reservation.exception.InvalidReservationStateException;
import com.flashsale.reservation.exception.PurchaseLimitExceededException;
import com.flashsale.reservation.exception.ReservationExpiredException;
import com.flashsale.reservation.exception.ReservationNotFoundException;
import com.flashsale.reservation.exception.ReservationOwnershipException;
import com.flashsale.reservation.exception.TicketTypeNotFoundException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ReservationExceptionHandler {
    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException exception) {
        return error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", exception.getMessage());
    }

    /** Lock contention during a flash sale: nothing changed, so tell the client to retry shortly (not a server error). */
    @ExceptionHandler(ReservationBusyException.class)
    ResponseEntity<ErrorResponse> handleBusy(ReservationBusyException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "1")
                .body(new ErrorResponse(false, new ErrorDetail("RESERVATION_BUSY", exception.getMessage()), Instant.now()));
    }

    @ExceptionHandler(InventoryUnavailableException.class)
    ResponseEntity<ErrorResponse> handleInventoryUnavailable(InventoryUnavailableException exception) {
        return error(HttpStatus.CONFLICT, "INVENTORY_UNAVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(InventoryNotFoundException.class)
    ResponseEntity<ErrorResponse> handleInventoryNotFound(InventoryNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "INVENTORY_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InventoryConflictException.class)
    ResponseEntity<ErrorResponse> handleInventoryConflict(InventoryConflictException exception) {
        return error(HttpStatus.CONFLICT, "INVENTORY_ALREADY_INITIALIZED", exception.getMessage());
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

    @ExceptionHandler(TicketTypeNotFoundException.class)
    ResponseEntity<ErrorResponse> handleTicketTypeNotFound(TicketTypeNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "TICKET_TYPE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(EventNotFoundException.class)
    ResponseEntity<ErrorResponse> handleEventNotFound(EventNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(EventCancelledException.class)
    ResponseEntity<ErrorResponse> handleEventCancelled(EventCancelledException exception) {
        return error(HttpStatus.CONFLICT, "EVENT_CANCELLED", exception.getMessage());
    }

    @ExceptionHandler(EventNotOnSaleException.class)
    ResponseEntity<ErrorResponse> handleEventNotOnSale(EventNotOnSaleException exception) {
        return error(HttpStatus.CONFLICT, "EVENT_NOT_ON_SALE", exception.getMessage());
    }

    @ExceptionHandler(CatalogUnavailableException.class)
    ResponseEntity<ErrorResponse> handleCatalogUnavailable(CatalogUnavailableException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Event eligibility could not be verified; please retry later");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message.isEmpty() ? "Request validation failed" : message);
    }

    /** Missing, non-JSON or wrongly typed bodies; the parser's own message is not returned because it names internal classes. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        if (exception.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            String field = mismatch.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                    .collect(Collectors.joining("."));
            return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid value for field '" + field + "'");
        }
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body is missing or is not valid JSON");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getHeaderName() + " header is required");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(false, new ErrorDetail(code, message), Instant.now()));
    }

    record ErrorResponse(boolean success, ErrorDetail error, Instant timestamp) { }
    record ErrorDetail(String code, String message) { }
}
