import type { AxiosInstance } from "axios";
import type { OrderSource } from "../data/OrderSource";
import type { OrderResponse, PaymentInitiationResponse, PaymentMethod } from "../types/order";

const orderPath = (orderId: string) => `/api/v1/orders/${encodeURIComponent(orderId)}`;

/** Orders and payments on the real backend (Order Service, which drives Payment Service over Kafka). */
export class ApiOrderSource implements OrderSource {
  readonly mode = "api" as const;

  constructor(private readonly http: AxiosInstance) {}

  async createOrder(reservationId: string, idempotencyKey: string): Promise<OrderResponse> {
    const response = await this.http.post<OrderResponse>("/api/v1/orders", { reservationId }, {
      headers: { "Idempotency-Key": idempotencyKey },
    });
    return response.data;
  }

  async order(orderId: string): Promise<OrderResponse> {
    return (await this.http.get<OrderResponse>(orderPath(orderId))).data;
  }

  async myOrders(): Promise<OrderResponse[]> {
    return (await this.http.get<OrderResponse[]>("/api/v1/orders/me")).data;
  }

  async cancelOrder(orderId: string): Promise<OrderResponse> {
    return (await this.http.post<OrderResponse>(`${orderPath(orderId)}/cancel`)).data;
  }

  async pay(orderId: string, method: PaymentMethod, idempotencyKey: string): Promise<PaymentInitiationResponse> {
    const response = await this.http.post<PaymentInitiationResponse>(`${orderPath(orderId)}/payment`, { paymentMethod: method }, {
      headers: { "Idempotency-Key": idempotencyKey },
    });
    return response.data;
  }
}
