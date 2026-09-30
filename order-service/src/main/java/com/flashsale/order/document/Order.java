package com.flashsale.order.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Objects;

/**
 * Order document stored in MongoDB.
 */
@Document(collection = "orders")
public class Order {

    @Id
    private String id;

    @Field("order_id")
    private String orderId;

    @Field("user_id")
    private String userId;

    @Field("reservation_id")
    private String reservationId;

    @Field("event_id")
    private String eventId;

    @Field("ticket_type_id")
    private String ticketTypeId;

    @Field("quantity")
    private int quantity;

    @Field("amount")
    private double amount;

    @Field("status")
    private OrderStatus status;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    // Constructors
    public Order() {}

    public Order(String userId, String reservationId, String eventId, String ticketTypeId, int quantity, double amount) {
        this.orderId = java.util.UUID.randomUUID().toString();
        this.userId = userId;
        this.reservationId = reservationId;
        this.eventId = eventId;
        this.ticketTypeId = ticketTypeId;
        this.quantity = quantity;
        this.amount = amount;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTicketTypeId() { return ticketTypeId; }
    public void setTicketTypeId(String ticketTypeId) { this.ticketTypeId = ticketTypeId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public OrderStatus getStatus() { return status; }

    public void setStatus(OrderStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    // State transition methods
    public void confirmPayment() {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Order must be in PENDING_PAYMENT to confirm payment");
        }
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    public void failPayment() {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Order must be in PENDING_PAYMENT to fail payment");
        }
        this.status = OrderStatus.PAYMENT_FAILED;
        this.updatedAt = Instant.now();
    }

    public void cancelOrder() {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only PENDING_PAYMENT orders can be cancelled");
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public void expireOrder() {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only PENDING_PAYMENT orders can expire");
        }
        this.status = OrderStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(orderId, order.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }
}