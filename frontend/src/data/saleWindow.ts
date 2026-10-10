import type { EventResponse } from "../types/catalog";

export type SalePhase = "upcoming" | "open" | "ended" | "cancelled";

/** Where an event's sale stands right now, from its status and sale window (PRD BR-006). */
export function salePhase(event: EventResponse, now = Date.now()): SalePhase {
  if (event.status === "CANCELLED") return "cancelled";
  if (event.status === "COMPLETED" || now >= Date.parse(event.saleEndTime)) return "ended";
  if (event.status === "DRAFT" || now < Date.parse(event.saleStartTime)) return "upcoming";
  return "open";
}
