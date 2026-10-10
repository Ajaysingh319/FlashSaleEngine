import { useCallback, useRef, useState } from "react";
import { AppError } from "../data/errors";
import { reservations } from "../data/sources";
import type { ReservationRequest, ReservationResponse } from "../types/reservation";
import { newIdempotencyKey } from "../utils/idempotencyKey";

export type ReservationState =
  | { status: "idle" }
  | { status: "submitting"; attempt: number }
  | { status: "reserved"; reservation: ReservationResponse }
  | { status: "failed"; error: AppError };

/** Busy answers (503 RESERVATION_BUSY) are retried this many times before giving up. */
const MAX_ATTEMPTS = 5;

/** Exponential back-off with jitter, or the server's Retry-After when it sent one. */
function retryDelayMs(attempt: number, error: AppError): number {
  if (error.retryAfterSeconds) return error.retryAfterSeconds * 1000;
  const ceiling = Math.min(2_000, 150 * 2 ** attempt);
  return ceiling / 2 + Math.random() * (ceiling / 2);
}

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * One reservation attempt at a time. A single Idempotency-Key covers the attempt and all of its retries, so a
 * retry after a lost response can never reserve twice. Success is reported only from the backend's answer.
 */
export function useReservation() {
  const [state, setState] = useState<ReservationState>({ status: "idle" });
  const inFlight = useRef(false);

  const reserve = useCallback(async (request: ReservationRequest) => {
    if (inFlight.current) return;
    inFlight.current = true;
    const idempotencyKey = newIdempotencyKey();
    try {
      for (let attempt = 1; ; attempt++) {
        setState({ status: "submitting", attempt });
        try {
          const reservation = await reservations.reserve(request, idempotencyKey);
          setState({ status: "reserved", reservation });
          return;
        } catch (caught) {
          const error = AppError.from(caught);
          if (error.kind !== "busy" || attempt >= MAX_ATTEMPTS) {
            setState({ status: "failed", error });
            return;
          }
          await wait(retryDelayMs(attempt, error));
        }
      }
    } finally {
      inFlight.current = false;
    }
  }, []);

  const reset = useCallback(() => setState({ status: "idle" }), []);

  return { state, reserve, reset };
}
