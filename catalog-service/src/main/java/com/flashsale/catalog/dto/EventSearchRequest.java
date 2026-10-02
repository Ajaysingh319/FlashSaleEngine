package com.flashsale.catalog.dto;

import com.flashsale.catalog.document.EventStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Query parameters for GET /api/v1/events (search, filters, sorting, pagination). */
public class EventSearchRequest {

    public static final String SORT_FIELD_PATTERN = "name|city|startTime|endTime|saleStartTime|saleEndTime|createdAt";

    /** Case-insensitive text search across name, description, venue and city. */
    @Size(max = 100)
    private String q;

    private EventStatus status;

    /** Case-insensitive exact city match. */
    @Size(max = 100)
    private String city;

    /** Inclusive lower bound on event start time (ISO-8601 instant). */
    private Instant startFrom;

    /** Inclusive upper bound on event start time (ISO-8601 instant). */
    private Instant startTo;

    @Pattern(regexp = SORT_FIELD_PATTERN, message = "must be one of: name, city, startTime, endTime, saleStartTime, saleEndTime, createdAt")
    private String sortBy = "startTime";

    @Pattern(regexp = "(?i)asc|desc", message = "must be asc or desc")
    private String direction = "asc";

    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(100)
    private int size = 20;

    @AssertTrue(message = "startFrom must not be after startTo")
    public boolean isStartRangeValid() {
        return startFrom == null || startTo == null || !startFrom.isAfter(startTo);
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public Instant getStartFrom() {
        return startFrom;
    }

    public void setStartFrom(Instant startFrom) {
        this.startFrom = startFrom;
    }

    public Instant getStartTo() {
        return startTo;
    }

    public void setStartTo(Instant startTo) {
        this.startTo = startTo;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
