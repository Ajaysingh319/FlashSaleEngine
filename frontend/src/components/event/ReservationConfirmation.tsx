import { CheckCircle2, Clock4, TimerOff } from "lucide-react";
import { Link } from "react-router-dom";
import { config } from "../../config/env";
import { useCountdown } from "../../hooks/useCountdown";
import type { ReservationResponse } from "../../types/reservation";
import { formatMoney, pad2 } from "../../utils/format";

/** The backend-confirmed reservation, with a live countdown to its server-provided expiry. */
export function ReservationConfirmation({ reservation, ticketTypeName, onReserveAgain }: {
  reservation: ReservationResponse;
  ticketTypeName: string;
  onReserveAgain: () => void;
}) {
  const { minutes, seconds, isOver, hours } = useCountdown(reservation.expiresAt);

  if (isOver) {
    return (
      <div className="rounded-3xl border border-line bg-white p-6 text-center shadow-card">
        <TimerOff className="mx-auto size-8 text-urgent" aria-hidden />
        <h2 className="mt-3 text-lg font-bold">Your reservation has expired</h2>
        <p className="mt-1 text-sm text-ink-muted">The 10-minute hold ended, so the tickets went back on sale.</p>
        <button type="button" onClick={onReserveAgain} className="mt-5 rounded-full bg-brand px-6 py-3 text-sm font-bold text-white transition hover:bg-brand-deep">
          Reserve again
        </button>
      </div>
    );
  }

  return (
    <div className="rounded-3xl border border-success/25 bg-success-soft p-6">
      <div className="flex items-start gap-3">
        <CheckCircle2 className="size-7 shrink-0 text-success" aria-hidden />
        <div>
          <h2 className="text-lg font-bold">Tickets reserved</h2>
          {config.dataMode === "demo" && (
            <p className="text-xs font-semibold text-urgent">Demo reservation: nothing was reserved on a real system.</p>
          )}
        </div>
      </div>

      <dl className="mt-5 grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
        <dt className="text-ink-muted">Reservation</dt>
        <dd className="truncate text-right font-mono text-xs leading-5">{reservation.reservationId}</dd>
        <dt className="text-ink-muted">Status</dt>
        <dd className="text-right font-semibold">{reservation.status}</dd>
        <dt className="text-ink-muted">Tickets</dt>
        <dd className="text-right font-semibold">{reservation.quantity} × {ticketTypeName}</dd>
        <dt className="text-ink-muted">Total</dt>
        <dd className="text-right text-lg font-extrabold text-brand">{formatMoney(reservation.amount)}</dd>
      </dl>

      <p className="mt-5 flex items-center justify-center gap-2 rounded-2xl bg-white px-4 py-3 text-sm font-semibold" role="timer">
        <Clock4 className="size-4 text-urgent" aria-hidden />
        Held for you for <span className="text-urgent tabular-nums">{hours > 0 ? `${hours}:` : ""}{pad2(minutes)}:{pad2(seconds)}</span>
      </p>

      <div className="mt-5 flex flex-col gap-2 sm:flex-row">
        <Link
          to={`/checkout/${encodeURIComponent(reservation.reservationId)}`}
          className="flex-1 rounded-full bg-ink px-6 py-3.5 text-center text-sm font-bold text-white transition hover:bg-black"
        >
          Continue to checkout
        </Link>
        <button type="button" onClick={onReserveAgain} className="flex-1 rounded-full border border-ink/20 bg-white px-6 py-3.5 text-sm font-bold transition hover:border-ink">
          Reserve other tickets
        </button>
      </div>
    </div>
  );
}
