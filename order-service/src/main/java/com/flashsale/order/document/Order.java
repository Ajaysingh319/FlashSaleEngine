package com.flashsale.order.document;

import com.flashsale.order.exception.InvalidOrderStateException;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/** Order-owned purchase snapshot. Reservation and catalog data are copied only after validation. */
@Document(collection = "orders")
public class Order {
    @Id private String id;
    @Indexed(unique = true) private String orderId;
    private String userId;
    @Indexed(unique = true) private String reservationId;
    private String eventId;
    private String ticketTypeId;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String paymentId;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private Instant createdAt;
    private Instant updatedAt;

    public void initialize(String orderId, String userId, String reservationId, String eventId, String ticketTypeId,
                           int quantity, BigDecimal unitPrice, Instant now) {
        this.orderId = orderId;
        this.userId = userId;
        this.reservationId = reservationId;
        this.eventId = eventId;
        this.ticketTypeId = ticketTypeId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        this.status = OrderStatus.PENDING_PAYMENT;
        this.paymentStatus = PaymentStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void confirmPayment(Instant now) { requirePendingPayment("confirm payment"); status = OrderStatus.CONFIRMED; paymentStatus = PaymentStatus.SUCCEEDED; updatedAt = now; }
    public void failPayment(Instant now) { requirePendingPayment("fail payment"); status = OrderStatus.PAYMENT_FAILED; paymentStatus = PaymentStatus.FAILED; updatedAt = now; }
    public void cancel(Instant now) { requirePendingPayment("cancel"); status = OrderStatus.CANCELLED; updatedAt = now; }
    public void expire(Instant now) { requirePendingPayment("expire"); status = OrderStatus.EXPIRED; updatedAt = now; }

    public void applyPaymentResult(String resultPaymentId, boolean succeeded, Instant now) {
        if (resultPaymentId == null || resultPaymentId.isBlank()) {
            throw new InvalidOrderStateException("Payment result must include a paymentId");
        }
        if (paymentId != null && !paymentId.equals(resultPaymentId)) {
            throw new InvalidOrderStateException("Payment result does not belong to this order");
        }
        if (succeeded) confirmPayment(now); else failPayment(now);
        paymentId = resultPaymentId;
    }

    private void requirePendingPayment(String action) {
        if (status != OrderStatus.PENDING_PAYMENT || paymentStatus != PaymentStatus.PENDING) {
            throw new InvalidOrderStateException("Only an order awaiting payment can " + action);
        }
    }

    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getOrderId() { return orderId; } public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getUserId() { return userId; } public void setUserId(String userId) { this.userId = userId; }
    public String getReservationId() { return reservationId; } public void setReservationId(String reservationId) { this.reservationId = reservationId; }
    public String getEventId() { return eventId; } public void setEventId(String eventId) { this.eventId = eventId; }
    public String getTicketTypeId() { return ticketTypeId; } public void setTicketTypeId(String ticketTypeId) { this.ticketTypeId = ticketTypeId; }
    public int getQuantity() { return quantity; } public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; } public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getTotalAmount() { return totalAmount; } public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getPaymentId() { return paymentId; } public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public OrderStatus getStatus() { return status; } public void setStatus(OrderStatus status) { this.status = status; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; } public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
