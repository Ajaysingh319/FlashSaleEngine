import type { AxiosInstance } from "axios";
import type { ReservationSource } from "../data/ReservationSource";
import type { ReservationRequest, ReservationResponse } from "../types/reservation";

const reservationPath = (reservationId: string) => `/api/v1/reservations/${encodeURIComponent(reservationId)}`;

/** Reservations on the real backend. The server enforces stock, limits and idempotency; this only asks. */
export class ApiReservationSource implements ReservationSource {
  readonly mode = "api" as const;

  constructor(private readonly http: AxiosInstance) {}

  async reserve(request: ReservationRequest, idempotencyKey: string): Promise<ReservationResponse> {
    const response = await this.http.post<ReservationResponse>("/api/v1/reservations", request, {
      headers: { "Idempotency-Key": idempotencyKey },
    });
    return response.data;
  }

  async reservation(reservationId: string): Promise<ReservationResponse> {
    return (await this.http.get<ReservationResponse>(reservationPath(reservationId))).data;
  }

  async myReservations(): Promise<ReservationResponse[]> {
    return (await this.http.get<ReservationResponse[]>("/api/v1/reservations/me")).data;
  }

  async cancel(reservationId: string): Promise<void> {
    await this.http.post(`${reservationPath(reservationId)}/cancel`);
  }
}
