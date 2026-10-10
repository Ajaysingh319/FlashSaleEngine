/**
 * Why an operation failed, in terms the UI can act on. The API client (Stage 5) maps HTTP statuses and backend
 * error codes onto these kinds; the demo backend raises the same ones, so pages handle both identically.
 */
export type ErrorKind =
  | "not-found"
  | "unauthorized" // 401: sign in again
  | "sold-out" // 409 INVENTORY_UNAVAILABLE
  | "purchase-limit" // 409 PURCHASE_LIMIT_EXCEEDED
  | "busy" // 503 RESERVATION_BUSY: retry shortly
  | "rate-limited" // 429
  | "sale-closed" // sale not open yet, ended, or event cancelled
  | "network" // no response
  | "unknown";

export class AppError extends Error {
  constructor(
    readonly kind: ErrorKind,
    message: string,
    /** Seconds the server asked us to wait before retrying, when it said so. */
    readonly retryAfterSeconds?: number,
  ) {
    super(message);
    this.name = "AppError";
  }

  static from(error: unknown): AppError {
    if (error instanceof AppError) return error;
    return new AppError("unknown", error instanceof Error ? error.message : String(error));
  }
}
