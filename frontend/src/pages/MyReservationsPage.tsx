import { Clock4, Ticket } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { AccountListSkeleton, AccountPage, EmptyAccountList } from "../components/account/AccountPage";
import { ReservationStatusBadge } from "../components/account/StatusBadge";
import { EventArt } from "../components/sale/EventArt";
import { ErrorState } from "../components/ui/StateMessage";
import { AppError } from "../data/errors";
import { reservations } from "../data/sources";
import { useAsync } from "../hooks/useAsync";
import { useCountdown } from "../hooks/useCountdown";
import { useEventLookup } from "../hooks/useEventLookup";
import type { ReservationResponse } from "../types/reservation";
import { formatDateTime, formatMoney, pad2 } from "../utils/format";

/** GET /api/v1/reservations/me: held tickets (with their 10-minute countdown) and past reservations. */
export function MyReservationsPage() {
  const list = useAsync(() => reservations.myReservations(), []);
  const events = useEventLookup(list.status === "success" ? list.data.map((reservation) => reservation.eventId) : []);
  const [actionError, setActionError] = useState<string | null>(null);

  const cancel = async (reservation: ReservationResponse) => {
    if (!window.confirm("Release these tickets so others can buy them?")) return;
    setActionError(null);
    try {
      await reservations.cancel(reservation.reservationId);
    } catch (error) {
      setActionError(`Could not cancel the reservation: ${AppError.from(error).message}`);
    }
    list.retry();
  };

  return (
    <AccountPage icon={Ticket} title="My Reservations" subtitle="Held tickets are kept for 10 minutes. Check out before the timer runs out.">
      {actionError && <p role="alert" className="mb-4 rounded-2xl bg-urgent-soft p-4 text-sm font-medium text-urgent">{actionError}</p>}
      {list.status === "loading" && <AccountListSkeleton />}
      {list.status === "error" && <ErrorState message={`Could not load your reservations: ${list.error.message}`} onRetry={list.retry} />}
      {list.status === "success" && list.data.length === 0 && (
        <EmptyAccountList title="No reservations yet" message="When you reserve tickets in a flash sale, they appear here while you check out." />
      )}
      {list.status === "success" && list.data.length > 0 && (
        <ul className="space-y-4">
          {list.data.map((reservation) => {
            const event = events.get(reservation.eventId) ?? null;
            return (
              <li key={reservation.reservationId} className="flex flex-col gap-4 rounded-3xl border border-line bg-white p-4 shadow-card sm:flex-row sm:items-center">
                <EventArt event={event} className="aspect-[4/3] w-full shrink-0 rounded-2xl sm:w-32" />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <ReservationStatusBadge status={reservation.status} />
                    {reservation.status === "ACTIVE" && <HoldCountdown expiresAt={reservation.expiresAt} onExpired={list.retry} />}
                    <span className="text-xs text-ink-muted">{formatDateTime(reservation.createdAt)}</span>
                  </div>
                  <h2 className="mt-2 truncate font-bold">{event?.name ?? "Event"}</h2>
                  <p className="text-sm text-ink-muted">
                    {reservation.quantity} ticket{reservation.quantity === 1 ? "" : "s"} · <span className="font-semibold text-ink">{formatMoney(reservation.amount)}</span>
                  </p>
                </div>
                {reservation.status === "ACTIVE" && (
                  <div className="flex gap-2 sm:flex-col">
                    <Link to={`/checkout/${encodeURIComponent(reservation.reservationId)}`} className="rounded-full bg-brand px-5 py-2.5 text-center text-sm font-bold text-white transition hover:bg-brand-deep">
                      Checkout
                    </Link>
                    <button type="button" onClick={() => cancel(reservation)} className="rounded-full border border-line px-5 py-2.5 text-sm font-bold text-ink-muted transition hover:border-urgent hover:text-urgent">
                      Release
                    </button>
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </AccountPage>
  );
}

/** Live time left on a hold; refreshes the list once it runs out so the expired status shows. */
function HoldCountdown({ expiresAt, onExpired }: { expiresAt: string; onExpired: () => void }) {
  const { hours, minutes, seconds, isOver } = useCountdown(expiresAt);
  useEffect(() => {
    if (!isOver) return;
    const timer = window.setTimeout(onExpired, 1_000);
    return () => window.clearTimeout(timer);
  }, [isOver, onExpired]);
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-urgent-soft px-2.5 py-1 text-xs font-semibold text-urgent tabular-nums" role="timer">
      <Clock4 className="size-3.5" aria-hidden />
      {isOver ? "Expired" : `${hours > 0 ? `${hours}:` : ""}${pad2(minutes)}:${pad2(seconds)} left`}
    </span>
  );
}
