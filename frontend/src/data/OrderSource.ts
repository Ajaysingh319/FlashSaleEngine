import type { DataMode } from "../config/env";
import type { OrderResponse, PaymentInitiationResponse, PaymentMethod } from "../types/order";

/** The signed-in user's orders and their payments (Order Service). Failures reject with an AppError. */
export interface OrderSource {
  readonly mode: DataMode;
  /** POST /api/v1/orders with Idempotency-Key: turns an active reservation into an order awaiting payment. */
  createOrder(reservationId: string, idempotencyKey: string): Promise<OrderResponse>;
  /** GET /api/v1/orders/{id} */
  order(orderId: string): Promise<OrderResponse>;
  /** GET /api/v1/orders/me */
  myOrders(): Promise<OrderResponse[]>;
  /** POST /api/v1/orders/{id}/cancel: only before payment starts; releases the reserved tickets. */
  cancelOrder(orderId: string): Promise<OrderResponse>;
  /** POST /api/v1/orders/{id}/payment with Idempotency-Key: accepted (202), settled asynchronously. */
  pay(orderId: string, method: PaymentMethod, idempotencyKey: string): Promise<PaymentInitiationResponse>;
}
