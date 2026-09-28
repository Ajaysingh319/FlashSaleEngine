package com.flashsale.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public class EventRequest {

    @NotBlank
    private String name;

    private String description;

    @NotBlank
    private String venue;

    @NotBlank
    private String city;

    @NotNull
    private Instant startTime;

    @NotNull
    private Instant endTime;

    @NotNull
    private Instant saleStartTime;

    @NotNull
    private Instant saleEndTime;

    private String status; // DRAFT, UPCOMING, ON_SALE, SOLD_OUT, COMPLETED, CANCELLED

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public Instant getSaleStartTime() {
        return saleStartTime;
    }

    public void setSaleStartTime(Instant saleStartTime) {
        this.saleStartTime = saleStartTime;
    }

    public Instant getSaleEndTime() {
        return saleEndTime;
    }

    public void setSaleEndTime(Instant saleEndTime) {
        this.saleEndTime = saleEndTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}