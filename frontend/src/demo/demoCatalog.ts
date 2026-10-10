import type { CatalogSource } from "../data/CatalogSource";
import type { EventResponse, EventStatus, InventoryResponse, TicketTypeResponse } from "../types/catalog";

/*
 * DEMO DATA ONLY: fictional events, venues and stock, used when VITE_DATA_MODE=demo. It is never combined with
 * API responses. Times are relative to page load so the countdowns are live.
 */

const HOUR = 3_600_000;
const DAY = 24 * HOUR;

interface DemoTier {
  name: string;
  price: number;
  total: number;
  available: number;
}

interface DemoEvent {
  id: string;
  name: string;
  description: string;
  venue: string;
  city: string;
  status: EventStatus;
  /** Offsets from now, in milliseconds. */
  saleStartsIn: number;
  saleEndsIn: number;
  startsIn: number;
  tiers: DemoTier[];
}

const DEMO_EVENTS: DemoEvent[] = [
  {
    id: "demo-neon-skyline",
    name: "Neon Skyline Live: Arena Tour",
    description: "An electro-pop night of lasers, live vocals and a 360° stage. One night only.",
    venue: "Skyline Arena",
    city: "Mumbai",
    status: "ON_SALE",
    saleStartsIn: -2 * HOUR,
    saleEndsIn: 5 * HOUR + 12 * 60_000,
    startsIn: 12 * DAY,
    tiers: [
      { name: "General", price: 2499, total: 300, available: 41 },
      { name: "VIP Floor", price: 7999, total: 100, available: 9 },
    ],
  },
  {
    id: "demo-monsoon-beats",
    name: "Monsoon Beats Festival",
    description: "Two stages, twenty artists and a rain-proof open-air garden for a full day of music.",
    venue: "Garden Grounds",
    city: "Bengaluru",
    status: "ON_SALE",
    saleStartsIn: -1 * DAY,
    saleEndsIn: 1 * DAY + 3 * HOUR,
    startsIn: 20 * DAY,
    tiers: [
      { name: "Day Pass", price: 1999, total: 800, available: 312 },
      { name: "Backstage Pass", price: 5499, total: 80, available: 22 },
    ],
  },
  {
    id: "demo-champions-night",
    name: "Champions Cricket Night",
    description: "Floodlit final under the stars. Every seat sold within minutes of opening.",
    venue: "Harbour Stadium",
    city: "Chennai",
    status: "ON_SALE",
    saleStartsIn: -3 * HOUR,
    saleEndsIn: 9 * HOUR,
    startsIn: 6 * DAY,
    tiers: [{ name: "Stand", price: 1499, total: 500, available: 0 }],
  },
  {
    id: "demo-comedy-gala",
    name: "Stand-up Saturdays: Comedy Gala",
    description: "Five headliners, one stage and two hours of brand-new material.",
    venue: "The Laugh Loft",
    city: "Delhi",
    status: "ON_SALE",
    saleStartsIn: -5 * HOUR,
    saleEndsIn: 2 * DAY + 6 * HOUR,
    startsIn: 9 * DAY,
    tiers: [
      { name: "Standard", price: 999, total: 150, available: 87 },
      { name: "Front Row", price: 2499, total: 30, available: 4 },
    ],
  },
  {
    id: "demo-symphony-stars",
    name: "Symphony Under the Stars",
    description: "A 60-piece orchestra performs film scores in an open-air amphitheatre.",
    venue: "Hilltop Amphitheatre",
    city: "Pune",
    status: "UPCOMING",
    saleStartsIn: 2 * DAY + 4 * HOUR,
    saleEndsIn: 5 * DAY,
    startsIn: 18 * DAY,
    tiers: [{ name: "Lawn", price: 1299, total: 400, available: 400 }],
  },
  {
    id: "demo-indie-premiere",
    name: "Indie Film Premiere Night",
    description: "Red carpet, a world premiere and a Q&A with the director and cast.",
    venue: "Lumière Cinema",
    city: "Hyderabad",
    status: "UPCOMING",
    saleStartsIn: 4 * DAY,
    saleEndsIn: 6 * DAY,
    startsIn: 25 * DAY,
    tiers: [{ name: "Premiere Seat", price: 1799, total: 120, available: 120 }],
  },
];

const SIMULATED_LATENCY_MS = 350;

export class DemoCatalogSource implements CatalogSource {
  readonly mode = "demo" as const;
  private readonly loadedAt = Date.now();

  eventsOnSale(): Promise<EventResponse[]> {
    return this.respond(DEMO_EVENTS.filter((event) => event.status === "ON_SALE").map((event) => this.toEvent(event)));
  }

  upcomingEvents(): Promise<EventResponse[]> {
    return this.respond(
      DEMO_EVENTS.filter((event) => event.status === "UPCOMING")
        .sort((a, b) => a.saleStartsIn - b.saleStartsIn)
        .map((event) => this.toEvent(event)),
    );
  }

  ticketTypes(eventId: string): Promise<TicketTypeResponse[]> {
    const event = this.find(eventId);
    return this.respond(
      event.tiers.map((tier, index) => ({
        id: `${event.id}-tt${index + 1}`,
        name: tier.name,
        price: tier.price,
        totalQuantity: tier.total,
        inventoryStatus: "READY" as const,
        eventId: event.id,
        createdAt: this.at(-7 * DAY),
        updatedAt: this.at(-7 * DAY),
      })),
    );
  }

  inventory(eventId: string): Promise<InventoryResponse[]> {
    const event = this.find(eventId);
    return this.respond(
      event.tiers.map((tier, index) => {
        const reserved = Math.min(tier.total - tier.available, Math.round(tier.total * 0.05));
        return {
          id: `${event.id}-tt${index + 1}`,
          eventId: event.id,
          ticketTypeId: `${event.id}-tt${index + 1}`,
          totalQuantity: tier.total,
          availableQuantity: tier.available,
          reservedQuantity: reserved,
          soldQuantity: tier.total - tier.available - reserved,
          updatedAt: this.at(0),
        };
      }),
    );
  }

  private find(eventId: string): DemoEvent {
    const event = DEMO_EVENTS.find((candidate) => candidate.id === eventId);
    if (!event) {
      throw new Error(`Demo event ${eventId} does not exist`);
    }
    return event;
  }

  private toEvent(event: DemoEvent): EventResponse {
    return {
      id: event.id,
      name: event.name,
      description: event.description,
      venue: event.venue,
      city: event.city,
      startTime: this.at(event.startsIn),
      endTime: this.at(event.startsIn + 3 * HOUR),
      saleStartTime: this.at(event.saleStartsIn),
      saleEndTime: this.at(event.saleEndsIn),
      status: event.status,
      createdAt: this.at(-7 * DAY),
      updatedAt: this.at(-1 * DAY),
    };
  }

  private at(offsetMs: number): string {
    return new Date(this.loadedAt + offsetMs).toISOString();
  }

  /** Resolves after a short delay, so loading states are visible in demo mode too. */
  private respond<T>(value: T): Promise<T> {
    return new Promise((resolve) => setTimeout(() => resolve(value), SIMULATED_LATENCY_MS));
  }
}
