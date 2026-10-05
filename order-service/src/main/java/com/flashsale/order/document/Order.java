package com.flashsale.order.document;

import com.flashsale.order.exception.InvalidOrderStateException;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/** Order-owned purchase snapshot. Reservation and catalog data are copied only after validation. */
@Document(collection = "orders")
@CompoundIndexes({
        @CompoundIndex(name = "status_reservation_expiry_idx", def = "{'status': 1, 'reservationExpiresAt': 1}"),
        // GET /orders/me and the admin order list (filter by event and status, newest first)
        @CompoundIndex(name = "user_created_idx", def = "{'userId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "event_status_created_idx", def = "{'eventId': 1, 'status': 1, 'createdAt': -1}")
})
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
    private String paymentMethod;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private Instant createdAt;
    private Instant updatedAt;
    /** Expiry of the backing reservation; an order still awaiting payment after this time expires (PRD 6.6, BR-005). */
    private Instant reservationExpiresAt;

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

    /** Payment initiation (PRD 6.8): one order has exactly one payment; from now on only its result settles the order. */
    public void startPayment(String newPaymentId, String method, Instant now) {
        requirePendingPayment("start payment");
        paymentId = newPaymentId;
        paymentMethod = method;
        paymentStatus = PaymentStatus.PROCESSING;
        updatedAt = now;
    }
    public boolean isAwaitingPaymentResult() { return status == OrderStatus.PENDING_PAYMENT && paymentStatus == PaymentStatus.PROCESSING; }
    public void confirmPayment(Instant now) { requireAwaitingPaymentResult(); status = OrderStatus.CONFIRMED; paymentStatus = PaymentStatus.SUCCEEDED; updatedAt = now; }
    public void failPayment(Instant now) { requireAwaitingPaymentResult(); status = OrderStatus.PAYMENT_FAILED; paymentStatus = PaymentStatus.FAILED; updatedAt = now; }
    /** The charge succeeded but the reserved tickets are gone (reservation expired or cancelled): refund it. */
    public void cancelWithRefund(Instant now) { requireAwaitingPaymentResult(); status = OrderStatus.CANCELLED; paymentStatus = PaymentStatus.REFUND_REQUESTED; updatedAt = now; }
    public boolean isAwaitingRefund() { return paymentStatus == PaymentStatus.REFUND_REQUESTED; }
    /** Payment Service confirmed the refund of a cancelled, unfulfillable order. */
    public void confirmRefund(Instant now) {
        if (!isAwaitingRefund()) {
            throw new InvalidOrderStateException("Order has no refund in progress");
        }
        paymentStatus = PaymentStatus.REFUNDED;
        updatedAt = now;
    }
    /** Checked before releasing the reservation, so an uncancellable order never touches Reservation Service. */
    public void requireCancellable() { requirePendingPayment("cancel"); }
    public void cancel(Instant now) { requirePendingPayment("cancel"); status = OrderStatus.CANCELLED; updatedAt = now; }
    public void expire(Instant now) { requirePendingPayment("expire"); status = OrderStatus.EXPIRED; updatedAt = now; }

    public void applyPaymentResult(String resultPaymentId, boolean succeeded, Instant now) {
        if (resultPaymentId == null || resultPaymentId.isBlank()) {
            throw new InvalidOrderStateException("Payment result must include a paymentId");
        }
        if (!resultPaymentId.equals(paymentId)) {
            throw new InvalidOrderStateException("Payment result does not belong to this order");
        }
        if (succeeded) confirmPayment(now); else failPayment(now);
    }

    /** Not paid and no payment started: the order can still be paid, cancelled or expired. */
    private void requirePendingPayment(String action) {
        if (status != OrderStatus.PENDING_PAYMENT || paymentStatus != PaymentStatus.PENDING) {
            throw new InvalidOrderStateException("Only an order awaiting payment can " + action);
        }
    }

    private void requireAwaitingPaymentResult() {
        if (!isAwaitingPaymentResult()) {
            throw new InvalidOrderStateException("Order has no payment in progress");
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
    public String getPaymentMethod() { return paymentMethod; } public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public OrderStatus getStatus() { return status; } public void setStatus(OrderStatus status) { this.status = status; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; } public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getReservationExpiresAt() { return reservationExpiresAt; } public void setReservationExpiresAt(Instant reservationExpiresAt) { this.reservationExpiresAt = reservationExpiresAt; }
}
