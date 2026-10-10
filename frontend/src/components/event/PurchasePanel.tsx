import { Loader2, ShieldCheck } from "lucide-react";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import { salePhase, type SalePhase } from "../../data/saleWindow";
import { useCountdown } from "../../hooks/useCountdown";
import { useReservation } from "../../hooks/useReservation";
import type { EventResponse, InventoryResponse, TicketTypeResponse } from "../../types/catalog";
import { formatDateTime, formatMoney } from "../../utils/format";
import { UrgencyCountdown } from "../sale/Countdown";
import { QuantitySelector } from "../sale/QuantitySelector";
import { StockIndicator } from "../sale/StockIndicator";
import { useToast } from "../ui/Toast";
import { ReservationConfirmation } from "./ReservationConfirmation";
import { ReservationErrorAlert } from "./ReservationErrorAlert";
import { TicketTypePicker } from "./TicketTypePicker";

/** PRD BR-002: at most 4 tickets per person per event; the backend enforces it, the UI just never offers more. */
const PURCHASE_LIMIT = 4;

const CLOSED_LABEL: Record<Exclude<SalePhase, "open">, string> = {
  upcoming: "Sale not open yet",
  ended: "Sale ended",
  cancelled: "Event cancelled",
};

export function PurchasePanel({ event, ticketTypes, inventory, onStockChanged }: {
  event: EventResponse;
  ticketTypes: TicketTypeResponse[];
  /** null while stock is loading or unavailable: stock is then not shown, never guessed. */
  inventory: InventoryResponse[] | null;
  onStockChanged: () => void;
}) {
  // Re-render at the sale-window boundaries so the button opens and closes on time.
  useCountdown(event.saleStartTime);
  useCountdown(event.saleEndTime);
  const phase = salePhase(event);

  const { state, reserve, reset } = useReservation();
  const toast = useToast();
  const stockOf = (id: string) => inventory?.find((item) => item.ticketTypeId === id) ?? null;

  const firstAvailable = useMemo(
    () => ticketTypes.find((type) => stockOf(type.id)?.availableQuantity !== 0)?.id ?? ticketTypes[0]?.id ?? null,
    [ticketTypes, inventory],
  );
  const [selectedId, setSelectedId] = useState<string | null>(firstAvailable);
  const [quantity, setQuantity] = useState(1);

  const selected = ticketTypes.find((type) => type.id === selectedId) ?? null;
  const stock = selected ? stockOf(selected.id) : null;
  const available = stock?.availableQuantity ?? null;
  const maxQuantity = Math.max(1, Math.min(PURCHASE_LIMIT, available ?? PURCHASE_LIMIT));

  useEffect(() => {
    if (selectedId === null || stockOf(selectedId)?.availableQuantity === 0) setSelectedId(firstAvailable);
  }, [firstAvailable]);
  useEffect(() => setQuantity((current) => Math.min(current, maxQuantity)), [maxQuantity]);

  useEffect(() => {
    if (state.status === "reserved") {
      toast("Tickets reserved. They are held for you for 10 minutes.");
      onStockChanged();
    } else if (state.status === "failed" && state.error.kind === "sold-out") {
      onStockChanged();
    }
  }, [state.status]);

  if (state.status === "reserved") {
    return (
      <ReservationConfirmation
        reservation={state.reservation}
        ticketTypeName={ticketTypes.find((type) => type.id === state.reservation.ticketTypeId)?.name ?? "Tickets"}
        onReserveAgain={reset}
      />
    );
  }

  if (ticketTypes.length === 0) {
    return <p className="rounded-2xl bg-brand-soft p-5 text-sm text-brand-deep">Tickets for this event are not on sale yet.</p>;
  }

  const submitting = state.status === "submitting";
  const soldOut = available === 0;
  const blockedLabel = phase !== "open" ? CLOSED_LABEL[phase] : soldOut ? "Sold out" : null;

  return (
    <div className="space-y-6">
      {phase === "open" && <UrgencyCountdown target={event.saleEndTime} title="Hurry up!" subtitle="Sale ends in" />}
      {phase === "upcoming" && <UrgencyCountdown target={event.saleStartTime} title="Coming soon" subtitle="Sale opens in" />}
      {phase === "ended" && <Notice>This sale ended on {formatDateTime(event.saleEndTime)}.</Notice>}
      {phase === "cancelled" && <Notice>This event was cancelled, so its tickets are not on sale.</Notice>}

      <TicketTypePicker ticketTypes={ticketTypes} inventory={inventory} selectedId={selectedId} onSelect={setSelectedId} disabled={submitting} />

      <StockIndicator available={available} total={stock?.totalQuantity ?? null} />

      <div className="flex flex-wrap items-center gap-4">
        <QuantitySelector value={quantity} max={maxQuantity} onChange={setQuantity} disabled={submitting || blockedLabel !== null} />
        <p className="text-xs text-ink-muted">Up to {PURCHASE_LIMIT} tickets per person</p>
        {selected && (
          <p className="ml-auto text-right">
            <span className="block text-xs text-ink-muted">Total</span>
            <span className="text-2xl font-extrabold text-brand">{formatMoney(selected.price * quantity)}</span>
          </p>
        )}
      </div>

      {state.status === "failed" && <ReservationErrorAlert error={state.error} onDismiss={reset} />}

      <button
        type="button"
        onClick={() => selected && reserve({ eventId: event.id, ticketTypeId: selected.id, quantity })}
        disabled={submitting || blockedLabel !== null || !selected}
        aria-busy={submitting}
        className="flex w-full items-center justify-center gap-2 rounded-full bg-brand px-6 py-4 text-base font-bold text-white shadow-lg shadow-brand/30 transition hover:bg-brand-deep disabled:cursor-not-allowed disabled:bg-line disabled:text-ink-muted disabled:shadow-none"
      >
        {submitting && <Loader2 className="size-5 animate-spin" aria-hidden />}
        {submitting
          ? state.attempt > 1 ? "High demand, still trying…" : "Reserving…"
          : blockedLabel ?? "Reserve Now"}
      </button>

      <p className="flex items-center gap-2 text-sm text-ink-muted">
        <ShieldCheck className="size-4 shrink-0 text-success" aria-hidden />
        Reserved tickets are held for 10 minutes while you check out. Shown stock can change at any moment.
      </p>
    </div>
  );
}

function Notice({ children }: { children: ReactNode }) {
  return <p className="rounded-2xl border border-line bg-brand-soft/60 px-4 py-3 text-sm font-medium">{children}</p>;
}
