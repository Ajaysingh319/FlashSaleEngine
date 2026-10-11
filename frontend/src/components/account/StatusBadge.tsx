import type { OrderResponse } from "../../types/order";
import type { ReservationStatus } from "../../types/reservation";
import { Badge, type BadgeTone } from "../ui/Badge";

const RESERVATION: Record<ReservationStatus, [string, BadgeTone]> = {
  ACTIVE: ["Held for you", "upcoming"],
  CONFIRMED: ["Purchased", "live"],
  CANCELLED: ["Cancelled", "muted"],
  EXPIRED: ["Expired", "muted"],
};

export function ReservationStatusBadge({ status }: { status: ReservationStatus }) {
  const [label, tone] = RESERVATION[status];
  return <Badge tone={tone}>{label}</Badge>;
}

/** One badge for an order, combining its status and payment status into what the customer cares about. */
export function OrderStatusBadge({ order }: { order: Pick<OrderResponse, "status" | "paymentStatus"> }) {
  const [label, tone] = orderLabel(order);
  return <Badge tone={tone}>{label}</Badge>;
}

function orderLabel({ status, paymentStatus }: Pick<OrderResponse, "status" | "paymentStatus">): [string, BadgeTone] {
  if (status === "CONFIRMED") return ["Confirmed", "live"];
  if (status === "PAYMENT_FAILED") return ["Payment failed", "urgent"];
  if (status === "EXPIRED") return ["Expired", "muted"];
  if (status === "CANCELLED") {
    if (paymentStatus === "REFUNDED") return ["Refunded", "muted"];
    if (paymentStatus === "REFUND_REQUESTED") return ["Refund in progress", "upcoming"];
    return ["Cancelled", "muted"];
  }
  return paymentStatus === "PROCESSING" ? ["Payment processing", "upcoming"] : ["Awaiting payment", "upcoming"];
}
