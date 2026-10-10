import type { DataMode } from "../config/env";
import type { ReservationRequest, ReservationResponse } from "../types/reservation";

/** Creates reservations. Failures reject with an AppError (sold-out, purchase-limit, busy, ...). */
export interface ReservationSource {
  readonly mode: DataMode;
  /** POST /api/v1/reservations with Idempotency-Key: the same key always means the same reservation attempt. */
  reserve(request: ReservationRequest, idempotencyKey: string): Promise<ReservationResponse>;
}
