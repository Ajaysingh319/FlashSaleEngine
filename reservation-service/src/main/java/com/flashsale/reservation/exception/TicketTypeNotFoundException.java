package com.flashsale.reservation.exception;

/** The ticket type does not exist in Catalog Service or does not belong to the requested event. */
public class TicketTypeNotFoundException extends RuntimeException {
    public TicketTypeNotFoundException(String ticketTypeId, String eventId) {
        super("Ticket type " + ticketTypeId + " not found for event " + eventId);
    }
}
