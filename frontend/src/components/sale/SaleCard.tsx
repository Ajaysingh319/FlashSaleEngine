import { CalendarClock, MapPin } from "lucide-react";
import { Link } from "react-router-dom";
import { isSoldOut, type SaleSummary } from "../../data/saleSummary";
import { formatDateTime, formatMoney } from "../../utils/format";
import { Badge } from "../ui/Badge";
import { CountdownChip } from "./Countdown";
import { EventArt } from "./EventArt";
import { StockIndicator } from "./StockIndicator";

/** A ticket drop: live sales show their closing countdown and stock, upcoming ones their opening time. */
export function SaleCard({ sale, upcoming = false }: { sale: SaleSummary; upcoming?: boolean }) {
  const { event, startingPrice, available, total } = sale;
  const soldOut = !upcoming && isSoldOut(sale);
  const detailsPath = `/events/${encodeURIComponent(event.id)}`;

  return (
    <article className="group flex flex-col rounded-3xl border border-line bg-white p-3 shadow-card transition duration-300 hover:-translate-y-1 hover:shadow-lift">
      <div className="relative">
        <EventArt event={event} className={`aspect-[4/3] w-full rounded-2xl ${soldOut ? "grayscale-[60%]" : ""}`} />
        <div className="absolute top-3 left-3">
          {upcoming ? (
            <Badge tone="upcoming">Upcoming</Badge>
          ) : soldOut ? (
            <Badge tone="muted">Sold out</Badge>
          ) : (
            <Badge tone="live" pulse>Live now</Badge>
          )}
        </div>
        {!upcoming && !soldOut && (
          <div className="absolute bottom-3 left-3">
            <CountdownChip target={event.saleEndTime} prefix="Ends in" />
          </div>
        )}
      </div>

      <div className="flex flex-1 flex-col gap-3 px-2 pt-4 pb-2">
        <div>
          <h3 className="line-clamp-2 text-base leading-snug font-bold">{event.name}</h3>
          <p className="mt-1 flex items-center gap-1 text-sm text-ink-muted">
            <MapPin className="size-3.5 shrink-0" aria-hidden />
            <span className="truncate">{event.city} · {event.venue}</span>
          </p>
        </div>

        {upcoming ? (
          <p className="flex items-center gap-1.5 text-sm font-medium text-brand-deep">
            <CalendarClock className="size-4" aria-hidden /> Sale opens {formatDateTime(event.saleStartTime)}
          </p>
        ) : (
          <StockIndicator available={available} total={total} />
        )}

        <div className="mt-auto flex items-end justify-between gap-3">
          <p className="text-xs text-ink-muted">
            {startingPrice === null ? "Prices coming soon" : "From"}
            {startingPrice !== null && <span className="block text-xl font-extrabold text-brand">{formatMoney(startingPrice)}</span>}
          </p>
          {soldOut ? (
            <span className="rounded-full bg-line px-5 py-2.5 text-sm font-semibold text-ink-muted">Sold out</span>
          ) : (
            <Link
              to={detailsPath}
              className={
                upcoming
                  ? "rounded-full border border-brand px-5 py-2.5 text-sm font-semibold text-brand transition hover:bg-brand-soft"
                  : "rounded-full bg-brand px-5 py-2.5 text-sm font-semibold text-white shadow-lg shadow-brand/25 transition hover:bg-brand-deep"
              }
            >
              {upcoming ? "View details" : "Reserve Now"}
            </Link>
          )}
        </div>
      </div>
    </article>
  );
}
