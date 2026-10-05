package com.flashsale.order.dto;

import com.flashsale.order.document.Order;

import java.time.Instant;
import java.math.BigDecimal;

/**
 * Response DTO for order.
 */
public class OrderResponse {

    private String orderId;
    private String userId;
    private String reservationId;
    private String eventId;
    private String ticketTypeId;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String status;
    private String paymentStatus;
    private Instant createdAt;
    private Instant updatedAt;

    public static OrderResponse from(Order order) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getOrderId());
        response.setUserId(order.getUserId());
        response.setReservationId(order.getReservationId());
        response.setEventId(order.getEventId());
        response.setTicketTypeId(order.getTicketTypeId());
        response.setQuantity(order.getQuantity());
        response.setUnitPrice(order.getUnitPrice());
        response.setTotalAmount(order.getTotalAmount());
        response.setStatus(order.getStatus().toString());
        response.setPaymentStatus(order.getPaymentStatus().toString());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        return response;
    }

    // Getters and setters
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getTicketTypeId() {
        return ticketTypeId;
    }

    public void setTicketTypeId(String ticketTypeId) {
        this.ticketTypeId = ticketTypeId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
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
