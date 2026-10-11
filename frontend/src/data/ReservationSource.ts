import type { DataMode } from "../config/env";
import type { ReservationRequest, ReservationResponse } from "../types/reservation";

/** The signed-in user's reservations. Failures reject with an AppError (sold-out, purchase-limit, busy, ...). */
export interface ReservationSource {
  readonly mode: DataMode;
  /** POST /api/v1/reservations with Idempotency-Key: the same key always means the same reservation attempt. */
  reserve(request: ReservationRequest, idempotencyKey: string): Promise<ReservationResponse>;
  /** GET /api/v1/reservations/{id} (owner only) */
  reservation(reservationId: string): Promise<ReservationResponse>;
  /** GET /api/v1/reservations/me */
  myReservations(): Promise<ReservationResponse[]>;
  /** POST /api/v1/reservations/{id}/cancel: returns the tickets to stock. */
  cancel(reservationId: string): Promise<void>;
}
