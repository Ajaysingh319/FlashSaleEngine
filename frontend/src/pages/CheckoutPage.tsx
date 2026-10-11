import { ArrowLeft, CheckCircle2, Clock4, CreditCard, Loader2, PartyPopper, XCircle } from "lucide-react";
import { useState, type ReactNode } from "react";
import { Link, useParams } from "react-router-dom";
import { OrderStatusBadge, ReservationStatusBadge } from "../components/account/StatusBadge";
import { EventArt } from "../components/sale/EventArt";
import { Skeleton } from "../components/ui/Skeleton";
import { ErrorState } from "../components/ui/StateMessage";
import { config } from "../config/env";
import { catalog } from "../data/sources";
import { useAsync } from "../hooks/useAsync";
import { checkoutStage, useCheckout, type CheckoutState } from "../hooks/useCheckout";
import { useCountdown } from "../hooks/useCountdown";
import type { PaymentMethod } from "../types/order";
import { formatMoney, pad2 } from "../utils/format";
import { NotFoundPage } from "./NotFoundPage";

const PAYMENT_METHODS: { value: PaymentMethod; label: string; hint: string }[] = [
  { value: "MOCK_CARD", label: "Test card", hint: "Approved by the simulated provider" },
  { value: "MOCK_CARD_DECLINED", label: "Test card: declined", hint: "Shows what happens when a payment fails" },
  { value: "MOCK_CARD_TIMEOUT", label: "Test card: no response", hint: "Simulates a provider timeout" },
];

/** Turns a held reservation into a paid order (PRD 7: reserve, order, pay), showing only backend-confirmed results. */
export function CheckoutPage() {
  const { reservationId = "" } = useParams();
  const { state, stage, pay, release, reload } = useCheckout(reservationId);
  const eventId = state.reservation?.eventId ?? "";
  const details = useAsync(
    () => (eventId ? Promise.all([catalog.event(eventId), catalog.ticketTypes(eventId)]) : Promise.resolve(null)),
    [eventId],
  );

  if (state.status === "loading") return <CheckoutSkeleton />;
  if (state.status === "failed" || !state.reservation) {
    if (state.error?.kind === "not-found" || state.error?.kind === "forbidden") return <NotFoundPage />;
    return (
      <div className="mx-auto max-w-3xl px-4 py-16">
        <ErrorState message={`Could not load this checkout: ${state.error?.message ?? "unknown error"}`} onRetry={reload} />
      </div>
    );
  }

  const { reservation, order } = state;
  const [event, ticketTypes] = details.status === "success" && details.data ? details.data : [null, []];
  const ticketTypeName = ticketTypes.find((type) => type.id === reservation.ticketTypeId)?.name ?? "Tickets";
  const total = order?.totalAmount ?? reservation.amount;

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
      <Link to="/reservations" className="inline-flex items-center gap-1.5 text-sm font-semibold text-ink-muted transition hover:text-brand">
        <ArrowLeft className="size-4" aria-hidden /> My reservations
      </Link>
      <h1 className="mt-4 text-3xl font-extrabold">Checkout</h1>

      <div className="mt-8 grid gap-8 lg:grid-cols-[1fr_1.1fr]">
        <section className="self-start rounded-[2rem] border border-line bg-white p-5 shadow-card" aria-label="Order summary">
          <EventArt event={event} className="aspect-[16/9] w-full rounded-2xl" />
          <h2 className="mt-5 text-xl font-bold">{event?.name ?? "Your tickets"}</h2>
          {event && <p className="text-sm text-ink-muted">{event.venue}, {event.city}</p>}
          <dl className="mt-5 space-y-2 text-sm">
            <Row label="Tickets">{reservation.quantity} × {ticketTypeName}</Row>
            <Row label="Price each">{formatMoney(order?.unitPrice ?? reservation.unitPrice)}</Row>
            <Row label="Reservation"><ReservationStatusBadge status={reservation.status} /></Row>
            {order && <Row label="Order"><OrderStatusBadge order={order} /></Row>}
          </dl>
          <div className="mt-5 flex items-center justify-between border-t border-line pt-4">
            <span className="font-semibold">Total</span>
            <span className="text-2xl font-extrabold text-brand">{formatMoney(total)}</span>
          </div>
        </section>

        <section className="space-y-6">
          <Steps state={state} />
          {stage === "payable" && <PaymentForm state={state} total={total} onPay={pay} onRelease={release} />}
          {stage === "processing" && <Processing slow={state.slow} />}
          {stage === "finished" && order && <Outcome order={order} eventId={reservation.eventId} />}
          {stage === "closed" && (
            <Panel icon={<XCircle className="size-7 text-urgent" aria-hidden />} title="These tickets are no longer held">
              The reservation {reservation.status === "EXPIRED" ? "expired" : "was cancelled"}, so the tickets went back on sale.
              <Actions eventId={reservation.eventId} primary="Reserve again" />
            </Panel>
          )}
          {state.error && (
            <p role="alert" className="rounded-2xl border border-urgent/25 bg-urgent-soft p-4 text-sm font-medium">
              {checkoutErrorMessage(state)}
            </p>
          )}
        </section>
      </div>
    </div>
  );
}

function checkoutErrorMessage({ error }: CheckoutState): string {
  switch (error?.kind) {
    case "expired": return "Your hold expired before payment started, so the tickets were released.";
    case "conflict": return "This order can no longer be changed. Its latest status is shown above.";
    case "unauthorized": return "Your session ended. Please sign in again.";
    case "unavailable":
    case "network": return "We could not reach the payment system. Nothing was charged; please try again.";
    default: return error?.message ?? "Something went wrong.";
  }
}

function PaymentForm({ state, total, onPay, onRelease }: {
  state: CheckoutState;
  total: number;
  onPay: (method: PaymentMethod) => void;
  onRelease: () => void;
}) {
  const [method, setMethod] = useState<PaymentMethod>("MOCK_CARD");
  return (
    <div className="space-y-5 rounded-[2rem] border border-line bg-white p-6 shadow-card">
      {state.reservation && <HoldTimer expiresAt={state.reservation.expiresAt} />}
      <fieldset>
        <legend className="mb-2 flex items-center gap-2 font-semibold"><CreditCard className="size-5 text-brand" aria-hidden /> Payment method</legend>
        <p className="mb-3 text-xs text-ink-muted">
          Simulated payments only: no card details are collected and no real money moves.
        </p>
        <div className="space-y-2">
          {PAYMENT_METHODS.map((option) => (
            <label key={option.value} className={`flex cursor-pointer items-start gap-3 rounded-2xl border-2 p-3 transition ${method === option.value ? "border-brand bg-brand-soft" : "border-line hover:border-brand/40"}`}>
              <input type="radio" name="payment-method" value={option.value} checked={method === option.value} onChange={() => setMethod(option.value)} className="mt-1 accent-brand" disabled={state.busy} />
              <span>
                <span className="block text-sm font-semibold">{option.label}</span>
                <span className="text-xs text-ink-muted">{option.hint}</span>
              </span>
            </label>
          ))}
        </div>
      </fieldset>
      <button
        type="button"
        onClick={() => onPay(method)}
        disabled={state.busy}
        aria-busy={state.busy}
        className="flex w-full items-center justify-center gap-2 rounded-full bg-brand px-6 py-4 font-bold text-white shadow-lg shadow-brand/30 transition hover:bg-brand-deep disabled:opacity-60"
      >
        {state.busy && <Loader2 className="size-5 animate-spin" aria-hidden />}
        {state.busy ? "Placing your order…" : `Pay ${formatMoney(total)}`}
      </button>
      <button type="button" onClick={onRelease} disabled={state.busy} className="w-full text-sm font-semibold text-ink-muted transition hover:text-urgent disabled:opacity-50">
        Cancel and release these tickets
      </button>
    </div>
  );
}

function HoldTimer({ expiresAt }: { expiresAt: string }) {
  const { hours, minutes, seconds, isOver } = useCountdown(expiresAt);
  return (
    <p className="flex items-center gap-2 rounded-2xl bg-urgent-soft px-4 py-3 text-sm font-semibold" role="timer">
      <Clock4 className="size-4 text-urgent" aria-hidden />
      {isOver ? "Your hold has ended." : <>Tickets held for <span className="text-urgent tabular-nums">{hours > 0 ? `${hours}:` : ""}{pad2(minutes)}:{pad2(seconds)}</span></>}
    </p>
  );
}

function Processing({ slow }: { slow: boolean }) {
  return (
    <Panel icon={<Loader2 className="size-7 animate-spin text-brand" aria-hidden />} title="Confirming your payment…">
      The payment is processed in the background; this page updates by itself as soon as the result arrives.
      {slow && <span className="mt-2 block font-semibold text-ink">This is taking longer than usual. The result will appear in My Orders.</span>}
    </Panel>
  );
}

function Outcome({ order, eventId }: { order: NonNullable<CheckoutState["order"]>; eventId: string }) {
  if (order.status === "CONFIRMED") {
    return (
      <Panel icon={<PartyPopper className="size-7 text-success" aria-hidden />} title="You're going! Tickets confirmed">
        Order <span className="font-mono text-xs">{order.orderId}</span> is paid and your tickets are yours.
        {config.dataMode === "demo" && <span className="mt-1 block text-xs font-semibold text-urgent">Demo order: nothing was charged or sold.</span>}
        <Actions eventId={eventId} primary="View my orders" primaryTo="/orders" />
      </Panel>
    );
  }
  const refund = order.paymentStatus === "REFUND_REQUESTED" || order.paymentStatus === "REFUNDED";
  const [title, text] =
    order.status === "PAYMENT_FAILED" ? ["Payment did not go through", "Nothing was charged, and the tickets were released for others."]
      : refund ? ["We could not confirm your tickets", `Your payment is being returned (${order.paymentStatus === "REFUNDED" ? "refunded" : "refund in progress"}).`]
        : order.status === "EXPIRED" ? ["Your order expired", "It was not paid in time, so the tickets were released."]
          : ["Order cancelled", "The tickets were released for others."];
  return (
    <Panel icon={<XCircle className="size-7 text-urgent" aria-hidden />} title={title}>
      {text}
      <Actions eventId={eventId} primary="Reserve again" />
    </Panel>
  );
}

function Steps({ state }: { state: CheckoutState }) {
  const stage = checkoutStage(state);
  const placed = state.order !== null;
  const paid = state.order?.status === "CONFIRMED";
  const steps = [
    { label: "Reserved", done: true },
    { label: "Order placed", done: placed },
    { label: stage === "processing" ? "Paying…" : "Paid", done: paid },
  ];
  return (
    <ol className="flex items-center gap-2" aria-label="Checkout progress">
      {steps.map((step, index) => (
        <li key={step.label} className="flex flex-1 items-center gap-2">
          <span className={`grid size-7 shrink-0 place-items-center rounded-full text-xs font-bold ${step.done ? "bg-success text-white" : "bg-line text-ink-muted"}`}>
            {step.done ? <CheckCircle2 className="size-4" aria-hidden /> : index + 1}
          </span>
          <span className={`text-sm font-semibold ${step.done ? "text-ink" : "text-ink-muted"}`}>{step.label}</span>
          {index < steps.length - 1 && <span className="h-px flex-1 bg-line" aria-hidden />}
        </li>
      ))}
    </ol>
  );
}

function Panel({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <div className="rounded-[2rem] border border-line bg-white p-6 shadow-card">
      <div className="flex items-start gap-3">
        {icon}
        <div className="flex-1">
          <h2 className="text-lg font-bold">{title}</h2>
          <div className="mt-1 text-sm text-ink-muted">{children}</div>
        </div>
      </div>
    </div>
  );
}

function Actions({ eventId, primary, primaryTo }: { eventId: string; primary: string; primaryTo?: string }) {
  return (
    <span className="mt-4 flex flex-wrap gap-2">
      <Link to={primaryTo ?? `/events/${encodeURIComponent(eventId)}`} className="rounded-full bg-brand px-5 py-2.5 text-sm font-bold text-white transition hover:bg-brand-deep">
        {primary}
      </Link>
      <Link to="/sales" className="rounded-full border border-line px-5 py-2.5 text-sm font-bold text-ink transition hover:border-ink">
        Browse sales
      </Link>
    </span>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <dt className="text-ink-muted">{label}</dt>
      <dd className="text-right font-semibold">{children}</dd>
    </div>
  );
}

function CheckoutSkeleton() {
  return (
    <div className="mx-auto grid max-w-6xl gap-8 px-4 py-16 sm:px-6 lg:grid-cols-2" aria-label="Loading checkout">
      <Skeleton className="h-96 rounded-[2rem]" />
      <Skeleton className="h-96 rounded-[2rem]" />
    </div>
  );
}
