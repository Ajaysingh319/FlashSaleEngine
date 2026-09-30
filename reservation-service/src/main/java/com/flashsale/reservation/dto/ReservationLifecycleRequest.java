package com.flashsale.reservation.dto;

import jakarta.validation.constraints.NotBlank;

/** Authenticated Order Service request for a reservation lifecycle transition. */
public class ReservationLifecycleRequest {
    @NotBlank private String orderId;
    @NotBlank private String userId;

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
