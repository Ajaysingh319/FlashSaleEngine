import { isAxiosError } from "axios";
import { AppError, type ErrorKind } from "../data/errors";

/** Backend error codes that mean something specific to the UI (Reservation and Auth error envelopes). */
const KIND_BY_CODE: Record<string, ErrorKind> = {
  INVENTORY_UNAVAILABLE: "sold-out",
  PURCHASE_LIMIT_EXCEEDED: "purchase-limit",
  RESERVATION_BUSY: "busy",
  EVENT_NOT_ON_SALE: "sale-closed",
  EVENT_CANCELLED: "sale-closed",
  EVENT_NOT_FOUND: "not-found",
  TICKET_TYPE_NOT_FOUND: "not-found",
  INVENTORY_NOT_FOUND: "not-found",
  RESERVATION_NOT_FOUND: "not-found",
  USER_NOT_FOUND: "not-found",
  INVALID_REQUEST: "invalid",
  RESERVATION_EXPIRED: "expired",
  SERVICE_UNAVAILABLE: "unavailable",
};

/** Used when the body has no code, e.g. Order Service's {status, error, message} envelope. */
const KIND_BY_STATUS: Record<number, ErrorKind> = {
  400: "invalid",
  401: "unauthorized",
  403: "forbidden",
  404: "not-found",
  409: "conflict",
  410: "expired",
  429: "rate-limited",
  502: "unavailable",
  503: "unavailable",
  504: "unavailable",
};

interface ErrorBody {
  error?: { code?: string; message?: string } | string; // Auth / Catalog / Reservation, or Order's "error" text
  message?: string; // Order Service
}

/**
 * Turns any failed request into an AppError. Handles every backend error format: {success, error:{code,
 * message}} (Auth, Catalog, Reservation), {status, error, message} (Order), and the Gateway's empty 401/403/429.
 */
export function toAppError(error: unknown): AppError {
  if (!isAxiosError(error)) return AppError.from(error);
  const response = error.response;
  if (!response) return new AppError("network", "Could not reach the server.");

  const body = (response.data ?? {}) as ErrorBody;
  const envelope = typeof body.error === "object" ? body.error : undefined;
  const code = envelope?.code;
  const message = envelope?.message ?? body.message ?? `Request failed with status ${response.status}`;
  const retryAfter = Number(response.headers["retry-after"]);

  const kind = (code && KIND_BY_CODE[code]) || KIND_BY_STATUS[response.status] || "unknown";
  return new AppError(kind, message, Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : undefined);
}
