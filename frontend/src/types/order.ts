/** Mirrors Order Service (PRD 16, TDD 34-36). */

export type OrderStatus = "PENDING_PAYMENT" | "CONFIRMED" | "PAYMENT_FAILED" | "CANCELLED" | "EXPIRED";

/** PENDING (not started) -> PROCESSING -> SUCCEEDED or FAILED; refunds when a paid order cannot be fulfilled. */
export type PaymentStatus = "PENDING" | "PROCESSING" | "SUCCEEDED" | "FAILED" | "REFUND_REQUESTED" | "REFUNDED";

/** POST /api/v1/orders, GET /api/v1/orders/{id}, GET /api/v1/orders/me */
export interface OrderResponse {
  orderId: string;
  userId: string;
  reservationId: string;
  eventId: string;
  ticketTypeId: string;
  quantity: number;
  unitPrice: number;
  totalAmount: number;
  status: OrderStatus;
  paymentStatus: PaymentStatus;
  createdAt: string;
  updatedAt: string;
}

/** The mock provider's test methods (PRD 6.8). No card details are ever collected. */
export type PaymentMethod = "MOCK_CARD" | "MOCK_CARD_DECLINED" | "MOCK_CARD_TIMEOUT";

/** POST /api/v1/orders/{id}/payment answers 202 with this; the result arrives asynchronously. */
export interface PaymentInitiationResponse {
  paymentId: string;
  orderId: string;
  status: string;
}
