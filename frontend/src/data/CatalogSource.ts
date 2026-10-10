import type { DataMode } from "../config/env";
import type { EventResponse, InventoryResponse, TicketTypeResponse } from "../types/catalog";

/**
 * Where catalog data comes from. The demo source and the API source (Stage 5) implement the same contract,
 * so pages never know, or mix, which one is active.
 */
export interface CatalogSource {
  readonly mode: DataMode;
  /** GET /api/v1/events/{eventId}; rejects with AppError("not-found") for an unknown event. */
  event(eventId: string): Promise<EventResponse>;
  /** GET /api/v1/events/on-sale */
  eventsOnSale(): Promise<EventResponse[]>;
  /** GET /api/v1/events?status=UPCOMING&sortBy=saleStartTime */
  upcomingEvents(): Promise<EventResponse[]>;
  /** GET /api/v1/events/{eventId}/ticket-types */
  ticketTypes(eventId: string): Promise<TicketTypeResponse[]>;
  /** GET /api/v1/events/{eventId}/inventory */
  inventory(eventId: string): Promise<InventoryResponse[]>;
}
