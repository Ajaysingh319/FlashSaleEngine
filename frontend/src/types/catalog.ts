/** Mirrors Catalog and Reservation Service responses (PRD 16). Instants arrive as ISO-8601 strings. */

export type EventStatus = "DRAFT" | "UPCOMING" | "ON_SALE" | "SOLD_OUT" | "COMPLETED" | "CANCELLED";

/** GET /api/v1/events, /events/on-sale, /events/{id} */
export interface EventResponse {
  id: string;
  name: string;
  description: string | null;
  venue: string;
  city: string;
  startTime: string;
  endTime: string;
  saleStartTime: string;
  saleEndTime: string;
  status: EventStatus;
  createdAt: string;
  updatedAt: string;
}

/** GET /api/v1/events/{id}/ticket-types (only READY types are listed) */
export interface TicketTypeResponse {
  id: string;
  name: string;
  price: number;
  totalQuantity: number;
  inventoryStatus: "PENDING" | "READY";
  eventId: string;
  createdAt: string;
  updatedAt: string;
}

/** GET /api/v1/events/{id}/inventory: display only, every reservation re-checks stock atomically. */
export interface InventoryResponse {
  id: string;
  eventId: string;
  ticketTypeId: string;
  totalQuantity: number;
  availableQuantity: number;
  reservedQuantity: number;
  soldQuantity: number;
  updatedAt: string;
}
