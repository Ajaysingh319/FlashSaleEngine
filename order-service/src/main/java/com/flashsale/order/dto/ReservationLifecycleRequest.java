package com.flashsale.order.dto;

/** Local internal request sent to Reservation when a payment outcome is finalized. */
public class ReservationLifecycleRequest {
    private String orderId;
    private String userId;

    public ReservationLifecycleRequest() { }
    public ReservationLifecycleRequest(String orderId, String userId) { this.orderId = orderId; this.userId = userId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
