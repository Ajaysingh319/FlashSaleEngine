package com.flashsale.catalog.document;

/** Event statuses defined in PRD 6.2. Stored on {@link Event} as the constant name. */
public enum EventStatus {
    DRAFT,
    UPCOMING,
    ON_SALE,
    SOLD_OUT,
    COMPLETED,
    CANCELLED;

    /** Regex matching exactly one status name, for request validation annotations. */
    public static final String NAME_PATTERN = "DRAFT|UPCOMING|ON_SALE|SOLD_OUT|COMPLETED|CANCELLED";
}
