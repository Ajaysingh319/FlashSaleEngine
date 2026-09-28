package com.flashsale.catalog.dto;

import java.time.Instant;

public class TicketTypeResponse {

    private String id;
    private String name;
    private Double price;
    private Integer totalQuantity;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private Integer soldQuantity;
    private String eventId;
    private Instant createdAt;
    private Instant updatedAt;

    public TicketTypeResponse() {
    }

    public TicketTypeResponse(String id, String name, Double price, Integer totalQuantity,
                              Integer availableQuantity, Integer reservedQuantity, Integer soldQuantity,
                              String eventId, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.totalQuantity = totalQuantity;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = reservedQuantity;
        this.soldQuantity = soldQuantity;
        this.eventId = eventId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Integer totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(Integer availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public Integer getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(Integer reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public Integer getSoldQuantity() {
        return soldQuantity;
    }

    public void setSoldQuantity(Integer soldQuantity) {
        this.soldQuantity = soldQuantity;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}