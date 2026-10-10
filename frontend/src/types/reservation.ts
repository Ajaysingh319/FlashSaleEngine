/** Mirrors Reservation Service (PRD 16, TDD 21). */

/** POST /api/v1/reservations body; the Idempotency-Key header is sent separately. */
export interface ReservationRequest {
  eventId: string;
  ticketTypeId: string;
  quantity: number;
}

export type ReservationStatus = "ACTIVE" | "CONFIRMED" | "CANCELLED" | "EXPIRED";

export interface ReservationResponse {
  reservationId: string;
  eventId: string;
  ticketTypeId: string;
  userId: string;
  quantity: number;
  status: ReservationStatus;
  createdAt: string;
  updatedAt: string;
  /** When the hold lapses (10 minutes after reserving, PRD BR-003). */
  expiresAt: string;
  unitPrice: number;
  amount: number;
}
