package com.flashsale.catalog.dto;

import com.flashsale.catalog.document.EventStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    // Optional: defaults to DRAFT on create and is left unchanged on update when omitted
    @Pattern(regexp = EventStatus.NAME_PATTERN, message = "must be one of: DRAFT, UPCOMING, ON_SALE, SOLD_OUT, COMPLETED, CANCELLED")
    private String status;

    @AssertTrue(message = "endTime must be after startTime")
    public boolean isEndTimeAfterStartTime() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }

    @AssertTrue(message = "saleEndTime must not be before saleStartTime")
    public boolean isSaleEndTimeNotBeforeSaleStartTime() {
        return saleStartTime == null || saleEndTime == null || !saleEndTime.isBefore(saleStartTime);
    }

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