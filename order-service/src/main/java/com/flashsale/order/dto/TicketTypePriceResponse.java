package com.flashsale.order.dto;

/** Minimal local representation of Catalog's existing ticket-type response. */
public class TicketTypePriceResponse {
    private String id;
    private String eventId;
    private Double price;

    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getEventId() { return eventId; } public void setEventId(String eventId) { this.eventId = eventId; }
    public Double getPrice() { return price; } public void setPrice(Double price) { this.price = price; }
}
