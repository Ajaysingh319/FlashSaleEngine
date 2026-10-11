import { AlertTriangle } from "lucide-react";
import { Link, useLocation } from "react-router-dom";
import type { AppError } from "../../data/errors";
import { loginPath } from "../../utils/redirect";

const MESSAGES: Record<AppError["kind"], string> = {
  "sold-out": "Those tickets were just taken by someone else. The stock above is now up to date.",
  "purchase-limit": "You can reserve up to 4 tickets per person for this event.",
  busy: "Demand is very high right now and we could not get your tickets. Please try again.",
  "rate-limited": "Too many attempts in a short time. Please wait a minute, then try again.",
  "sale-closed": "This sale is not open right now.",
  unauthorized: "Please sign in to reserve tickets.",
  forbidden: "Your account is not allowed to do this.",
  invalid: "Some details of the request were not accepted.",
  "not-found": "This ticket type is no longer available.",
  conflict: "This request clashed with an earlier one. Please try again.",
  expired: "Your hold on these tickets has expired.",
  unavailable: "A part of the system is temporarily unavailable. Please try again shortly.",
  network: "We could not reach the server. Check your connection and try again.",
  unknown: "Something went wrong while reserving.",
};

/** Explains a failed reservation and offers the one action that helps. */
export function ReservationErrorAlert({ error, onDismiss }: { error: AppError; onDismiss: () => void }) {
  const location = useLocation();
  const waitHint = error.kind === "rate-limited" && error.retryAfterSeconds ? ` (about ${error.retryAfterSeconds}s)` : "";
  return (
    <div role="alert" className="flex gap-3 rounded-2xl border border-urgent/25 bg-urgent-soft p-4 text-sm">
      <AlertTriangle className="size-5 shrink-0 text-urgent" aria-hidden />
      <div className="flex-1">
        <p className="font-semibold text-ink">{MESSAGES[error.kind]}{waitHint}</p>
        <div className="mt-3 flex flex-wrap gap-2">
          {error.kind === "unauthorized" ? (
            <Link to={loginPath(location.pathname)} className="rounded-full bg-urgent px-4 py-2 font-semibold text-white">Sign in</Link>
          ) : (
            <button type="button" onClick={onDismiss} className="rounded-full bg-white px-4 py-2 font-semibold text-urgent shadow-sm">
              {error.kind === "sold-out" || error.kind === "purchase-limit" ? "Choose again" : "Try again"}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
