import type { EventResponse, InventoryResponse, TicketTypeResponse } from "../types/catalog";
import type { CatalogSource } from "./CatalogSource";

/** What a sale card shows: the event plus figures derived only from real ticket-type and inventory data. */
export interface SaleSummary {
  event: EventResponse;
  /** Lowest ticket price, or null when no ticket type is set up yet. */
  startingPrice: number | null;
  /** Tickets left across all ticket types, or null when stock is not available. */
  available: number | null;
  total: number | null;
}

export function summarize(
  event: EventResponse,
  ticketTypes: TicketTypeResponse[],
  inventory: InventoryResponse[],
): SaleSummary {
  const prices = ticketTypes.map((type) => type.price);
  const hasStock = inventory.length > 0;
  return {
    event,
    startingPrice: prices.length > 0 ? Math.min(...prices) : null,
    available: hasStock ? inventory.reduce((sum, item) => sum + item.availableQuantity, 0) : null,
    total: hasStock ? inventory.reduce((sum, item) => sum + item.totalQuantity, 0) : null,
  };
}

export async function loadSummaries(source: CatalogSource, events: EventResponse[]): Promise<SaleSummary[]> {
  return Promise.all(
    events.map(async (event) => {
      const [ticketTypes, inventory] = await Promise.all([source.ticketTypes(event.id), source.inventory(event.id)]);
      return summarize(event, ticketTypes, inventory);
    }),
  );
}

export const isSoldOut = (sale: SaleSummary) => sale.event.status === "SOLD_OUT" || sale.available === 0;

/** The on-sale event closing soonest: the most urgent one to feature. */
export function pickFeatured(sales: SaleSummary[], now = Date.now()): SaleSummary | null {
  const open = sales.filter((sale) => !isSoldOut(sale) && Date.parse(sale.event.saleEndTime) > now);
  open.sort((a, b) => Date.parse(a.event.saleEndTime) - Date.parse(b.event.saleEndTime));
  return open[0] ?? null;
}
