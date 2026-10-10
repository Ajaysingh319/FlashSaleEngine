import { ArrowLeft, CalendarDays, MapPin } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { PurchasePanel } from "../components/event/PurchasePanel";
import { CountdownChip } from "../components/sale/Countdown";
import { EventArt } from "../components/sale/EventArt";
import { themeFor } from "../components/sale/eventArtwork";
import { Badge } from "../components/ui/Badge";
import { Skeleton } from "../components/ui/Skeleton";
import { ErrorState } from "../components/ui/StateMessage";
import { AppError } from "../data/errors";
import { salePhase } from "../data/saleWindow";
import { catalog } from "../data/sources";
import { useAsync } from "../hooks/useAsync";
import type { EventResponse } from "../types/catalog";
import { formatDateTime } from "../utils/format";
import { NotFoundPage } from "./NotFoundPage";

/** Split layout after the reference product page: artwork left, details and purchase panel right. */
export function EventDetailsPage() {
  const { eventId = "" } = useParams();
  const details = useAsync(() => Promise.all([catalog.event(eventId), catalog.ticketTypes(eventId)]), [eventId]);
  // Stock loads separately, so it can refresh after a reservation without reloading the whole page.
  const inventory = useAsync(() => catalog.inventory(eventId), [eventId]);

  if (details.status === "loading") return <DetailsSkeleton />;
  if (details.status === "error") {
    if (AppError.from(details.error).kind === "not-found") return <NotFoundPage />;
    return (
      <div className="mx-auto max-w-3xl px-4 py-16">
        <ErrorState message={`Could not load this event: ${details.error.message}`} onRetry={details.retry} />
      </div>
    );
  }

  const [event, ticketTypes] = details.data;
  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      <Link to="/sales" className="inline-flex items-center gap-1.5 text-sm font-semibold text-ink-muted transition hover:text-brand">
        <ArrowLeft className="size-4" aria-hidden /> All flash sales
      </Link>

      <div className="mt-6 grid gap-10 lg:grid-cols-2 lg:gap-14">
        <EventGallery event={event} />

        <div>
          <p className="text-xs font-bold tracking-widest text-ink-muted uppercase">{themeFor(event).label}</p>
          <h1 className="mt-2 text-3xl leading-tight font-extrabold tracking-tight sm:text-4xl">{event.name}</h1>
          <div className="mt-3 flex flex-wrap gap-x-5 gap-y-2 text-sm text-ink-muted">
            <span className="inline-flex items-center gap-1.5"><CalendarDays className="size-4" aria-hidden />{formatDateTime(event.startTime)}</span>
            <span className="inline-flex items-center gap-1.5"><MapPin className="size-4" aria-hidden />{event.venue}, {event.city}</span>
          </div>
          {event.description && <p className="mt-5 leading-relaxed text-ink/80">{event.description}</p>}

          <div className="mt-8">
            <PurchasePanel
              event={event}
              ticketTypes={ticketTypes}
              inventory={inventory.status === "success" ? inventory.data : null}
              onStockChanged={inventory.retry}
            />
            {inventory.status === "error" && (
              <p className="mt-3 text-xs text-ink-muted">
                Live stock is unavailable right now; you can still try to reserve.{" "}
                <button type="button" onClick={inventory.retry} className="font-semibold text-brand">Refresh stock</button>
              </p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function EventGallery({ event }: { event: EventResponse }) {
  const phase = salePhase(event);
  return (
    <div className="relative self-start lg:sticky lg:top-24">
      <EventArt event={event} large className="aspect-square w-full rounded-[2rem] shadow-card sm:aspect-[5/4]" />
      <div className="absolute top-5 left-5">
        {phase === "open" ? <Badge tone="live" pulse>Live now</Badge> : phase === "upcoming" ? <Badge tone="upcoming">Upcoming</Badge> : <Badge tone="muted">{phase === "ended" ? "Sale ended" : "Cancelled"}</Badge>}
      </div>
      {phase === "open" && (
        <div className="absolute bottom-6 left-1/2 -translate-x-1/2 scale-110">
          <CountdownChip target={event.saleEndTime} prefix="Ends in" />
        </div>
      )}
    </div>
  );
}

function DetailsSkeleton() {
  return (
    <div className="mx-auto grid max-w-7xl gap-10 px-4 py-14 sm:px-6 lg:grid-cols-2" aria-label="Loading event">
      <Skeleton className="aspect-square rounded-[2rem] sm:aspect-[5/4]" />
      <div className="space-y-4">
        <Skeleton className="h-4 w-24" />
        <Skeleton className="h-10 w-4/5" />
        <Skeleton className="h-4 w-2/3" />
        <Skeleton className="h-20 w-full" />
        <Skeleton className="h-16 w-full rounded-2xl" />
        <Skeleton className="h-14 w-full rounded-full" />
      </div>
    </div>
  );
}
