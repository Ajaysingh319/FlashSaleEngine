import type { AxiosInstance } from "axios";
import type { CatalogSource } from "../data/CatalogSource";
import type { EventResponse, InventoryResponse, TicketTypeResponse } from "../types/catalog";

const eventPath = (eventId: string) => `/api/v1/events/${encodeURIComponent(eventId)}`;

/** Catalog and inventory from the real backend (Catalog and Reservation Services, via the Gateway). */
export class ApiCatalogSource implements CatalogSource {
  readonly mode = "api" as const;

  constructor(private readonly http: AxiosInstance) {}

  async event(eventId: string): Promise<EventResponse> {
    return (await this.http.get<EventResponse>(eventPath(eventId))).data;
  }

  async eventsOnSale(): Promise<EventResponse[]> {
    return (await this.http.get<EventResponse[]>("/api/v1/events/on-sale")).data;
  }

  async upcomingEvents(): Promise<EventResponse[]> {
    const params = { status: "UPCOMING", sortBy: "saleStartTime", direction: "asc", size: 20 };
    return (await this.http.get<EventResponse[]>("/api/v1/events", { params })).data;
  }

  async ticketTypes(eventId: string): Promise<TicketTypeResponse[]> {
    return (await this.http.get<TicketTypeResponse[]>(`${eventPath(eventId)}/ticket-types`)).data;
  }

  async inventory(eventId: string): Promise<InventoryResponse[]> {
    return (await this.http.get<InventoryResponse[]>(`${eventPath(eventId)}/inventory`)).data;
  }
}
