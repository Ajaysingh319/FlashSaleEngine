import { Package } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { OrderStatusBadge } from "../components/account/StatusBadge";
import { AccountListSkeleton, AccountPage, EmptyAccountList } from "../components/account/AccountPage";
import { EventArt } from "../components/sale/EventArt";
import { ErrorState } from "../components/ui/StateMessage";
import { AppError } from "../data/errors";
import { orders } from "../data/sources";
import { useAsync } from "../hooks/useAsync";
import { useEventLookup } from "../hooks/useEventLookup";
import type { OrderResponse } from "../types/order";
import { formatDateTime, formatMoney } from "../utils/format";

/** GET /api/v1/orders/me: every order with its status, and the action that applies to it. */
export function MyOrdersPage() {
  const list = useAsync(() => orders.myOrders(), []);
  const events = useEventLookup(list.status === "success" ? list.data.map((order) => order.eventId) : []);
  const [actionError, setActionError] = useState<string | null>(null);

  const cancel = async (order: OrderResponse) => {
    if (!window.confirm("Cancel this order and release its tickets?")) return;
    setActionError(null);
    try {
      await orders.cancelOrder(order.orderId);
    } catch (error) {
      setActionError(`Could not cancel the order: ${AppError.from(error).message}`);
    }
    list.retry();
  };

  return (
    <AccountPage icon={Package} title="My Orders" subtitle="Newest first. Payments settle in the background, so a new order can show as processing for a moment.">
      {actionError && <p role="alert" className="mb-4 rounded-2xl bg-urgent-soft p-4 text-sm font-medium text-urgent">{actionError}</p>}
      {list.status === "loading" && <AccountListSkeleton />}
      {list.status === "error" && <ErrorState message={`Could not load your orders: ${list.error.message}`} onRetry={list.retry} />}
      {list.status === "success" && list.data.length === 0 && (
        <EmptyAccountList title="No orders yet" message="Reserve tickets in a flash sale, then complete checkout to see your order here." />
      )}
      {list.status === "success" && list.data.length > 0 && (
        <ul className="space-y-4">
          {list.data.map((order) => {
            const event = events.get(order.eventId) ?? null;
            const awaitingPayment = order.status === "PENDING_PAYMENT" && order.paymentStatus === "PENDING";
            return (
              <li key={order.orderId} className="flex flex-col gap-4 rounded-3xl border border-line bg-white p-4 shadow-card sm:flex-row sm:items-center">
                <EventArt event={event} className="aspect-[4/3] w-full shrink-0 rounded-2xl sm:w-32" />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <OrderStatusBadge order={order} />
                    <span className="text-xs text-ink-muted">{formatDateTime(order.createdAt)}</span>
                  </div>
                  <h2 className="mt-2 truncate font-bold">{event?.name ?? "Event"}</h2>
                  <p className="text-sm text-ink-muted">
                    {order.quantity} ticket{order.quantity === 1 ? "" : "s"} · <span className="font-semibold text-ink">{formatMoney(order.totalAmount)}</span>
                  </p>
                  <p className="mt-1 truncate font-mono text-xs text-ink-muted">{order.orderId}</p>
                </div>
                <div className="flex gap-2 sm:flex-col">
                  {order.status === "PENDING_PAYMENT" && (
                    <Link to={`/checkout/${encodeURIComponent(order.reservationId)}`} className="rounded-full bg-brand px-5 py-2.5 text-center text-sm font-bold text-white transition hover:bg-brand-deep">
                      {awaitingPayment ? "Pay now" : "View payment"}
                    </Link>
                  )}
                  {awaitingPayment && (
                    <button type="button" onClick={() => cancel(order)} className="rounded-full border border-line px-5 py-2.5 text-sm font-bold text-ink-muted transition hover:border-urgent hover:text-urgent">
                      Cancel
                    </button>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </AccountPage>
  );
}
