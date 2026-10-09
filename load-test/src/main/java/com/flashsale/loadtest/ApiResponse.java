package com.flashsale.loadtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

/** One Gateway answer. Status 0 means the request never got an HTTP response (connection refused, timeout). */
record ApiResponse(int status, JsonNode body) {
    static final int NO_RESPONSE = 0;

    static ApiResponse noResponse() {
        return new ApiResponse(NO_RESPONSE, MissingNode.getInstance());
    }

    boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    /** Reservation's deliberate back-pressure under contention (503 RESERVATION_BUSY): retry, not a failure. */
    boolean isBusy() {
        return status == 503 && "RESERVATION_BUSY".equals(body.path("error").path("code").asText());
    }

    /** A failure the system itself caused: no response or a 5xx other than back-pressure. 4xx are business outcomes. */
    boolean isError() {
        return status == NO_RESPONSE || status >= 500 && !isBusy();
    }

    String field(String name) {
        return body.path(name).asText();
    }
}
