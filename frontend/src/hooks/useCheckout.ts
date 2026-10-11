import { useCallback, useEffect, useState } from "react";
import { AppError } from "../data/errors";
import { orders, reservations } from "../data/sources";
import type { OrderResponse, PaymentMethod } from "../types/order";
import type { ReservationResponse } from "../types/reservation";
import { newIdempotencyKey } from "../utils/idempotencyKey";

const POLL_EVERY_MS = 1_500;
const POLL_FOR_MS = 60_000;

/**
 * Idempotency keys for one reservation's checkout, kept for the tab's lifetime: a reload or retry reuses them, so
 * the backend replays the same order and payment instead of creating new ones.
 */
function checkoutKeys(reservationId: string): { order: string; payment: string } {
  const storageKey = `flashsale.checkout.${reservationId}`;
  try {
    const saved = sessionStorage.getItem(storageKey);
    if (saved) return JSON.parse(saved);
    const keys = { order: newIdempotencyKey(), payment: newIdempotencyKey() };
    sessionStorage.setItem(storageKey, JSON.stringify(keys));
    return keys;
  } catch {
    return { order: newIdempotencyKey(), payment: newIdempotencyKey() };
  }
}

/** The checkout's position, derived only from backend state. */
export type CheckoutStage =
  | "payable" // active reservation, payment not started
  | "processing" // payment sent, waiting for Payment Service's result
  | "finished" // the order reached a final status
  | "closed"; // the reservation can no longer be bought (expired or cancelled, no order)

export interface CheckoutState {
  status: "loading" | "ready" | "failed";
  reservation: ReservationResponse | null;
  order: OrderResponse | null;
  /** Error from the last action (or from loading, when status is "failed"). */
  error: AppError | null;
  busy: boolean;
  /** The payment result is taking longer than polling waits for. */
  slow: boolean;
}

export function checkoutStage({ reservation, order }: CheckoutState): CheckoutStage {
  if (order?.paymentStatus === "PROCESSING") return "processing";
  if (order && order.status !== "PENDING_PAYMENT") return "finished";
  return reservation?.status === "ACTIVE" ? "payable" : "closed";
}

export function useCheckout(reservationId: string) {
  const [state, setState] = useState<CheckoutState>({
    status: "loading", reservation: null, order: null, error: null, busy: false, slow: false,
  });
  const patch = (changes: Partial<CheckoutState>) => setState((current) => ({ ...current, ...changes }));

  const load = useCallback(async () => {
    try {
      const [reservation, myOrders] = await Promise.all([reservations.reservation(reservationId), orders.myOrders()]);
      const order = myOrders.find((candidate) => candidate.reservationId === reservationId) ?? null;
      patch({ status: "ready", reservation, order, error: null });
    } catch (error) {
      patch({ status: "failed", error: AppError.from(error) });
    }
  }, [reservationId]);

  useEffect(() => {
    load();
  }, [load]);

  // While the payment settles asynchronously (Kafka round trip), poll the order until it leaves PROCESSING.
  const orderId = state.order?.orderId;
  const processing = state.order?.paymentStatus === "PROCESSING";
  useEffect(() => {
    if (!processing || !orderId) return;
    const startedAt = Date.now();
    const timer = window.setInterval(async () => {
      if (Date.now() - startedAt > POLL_FOR_MS) {
        window.clearInterval(timer);
        patch({ slow: true });
        return;
      }
      try {
        const order = await orders.order(orderId);
        if (order.paymentStatus !== "PROCESSING") {
          window.clearInterval(timer);
          patch({ order, reservation: await reservations.reservation(reservationId).catch(() => null) });
        }
      } catch {
        // A missed poll is retried on the next tick.
      }
    }, POLL_EVERY_MS);
    return () => window.clearInterval(timer);
  }, [processing, orderId, reservationId]);

  /** Creates the order (once) and starts the payment (once); retries reuse the same keys. */
  const pay = useCallback(
    async (method: PaymentMethod) => {
      const keys = checkoutKeys(reservationId);
      patch({ busy: true, error: null, slow: false });
      try {
        let order = state.order;
        if (!order) {
          try {
            order = await orders.createOrder(reservationId, keys.order);
          } catch (error) {
            // 409: this reservation already has an order (e.g. created in another tab): continue with that one.
            if (AppError.from(error).kind !== "conflict") throw error;
            order = (await orders.myOrders()).find((candidate) => candidate.reservationId === reservationId) ?? null;
            if (!order) throw error;
          }
        }
        await orders.pay(order.orderId, method, keys.payment);
        patch({ order: await orders.order(order.orderId), busy: false });
      } catch (error) {
        patch({ busy: false, error: AppError.from(error) });
        if (AppError.from(error).kind === "expired") load();
      }
    },
    [reservationId, state.order, load],
  );

  /** Gives the tickets back: cancels the order if there is one, otherwise the reservation. */
  const release = useCallback(async () => {
    patch({ busy: true, error: null });
    try {
      if (state.order) await orders.cancelOrder(state.order.orderId);
      else await reservations.cancel(reservationId);
      await load();
    } catch (error) {
      patch({ error: AppError.from(error) });
    } finally {
      patch({ busy: false });
    }
  }, [reservationId, state.order, load]);

  return { state, stage: checkoutStage(state), pay, release, reload: load };
}
