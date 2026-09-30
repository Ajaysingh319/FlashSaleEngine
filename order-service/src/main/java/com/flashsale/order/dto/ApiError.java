package com.flashsale.order.dto;

import java.time.Instant;

/** Stable error envelope returned by Order's HTTP API. */
public record ApiError(Instant timestamp, int status, String error, String message) { }
