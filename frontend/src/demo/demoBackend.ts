import type { CatalogSource } from "../data/CatalogSource";
import { AppError } from "../data/errors";
import type { ReservationSource } from "../data/ReservationSource";
import { salePhase } from "../data/saleWindow";
import type { EventResponse, EventStatus, InventoryResponse, TicketTypeResponse } from "../types/catalog";
import type { ReservationRequest, ReservationResponse } from "../types/reservation";

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
/** PRD BR-002 and BR-003, mirrored so the demo behaves like Reservation Service. */
const PURCHASE_LIMIT = 4;
const HOLD_MS = 10 * 60_000;

interface DemoStock {
  total: number;
  initiallyAvailable: number;
  available: number;
}

/**
 * In-memory stand-in for the backend: serves the catalog and accepts reservations against demo stock, enforcing
 * the same rules (sale window, stock, 4 per person per event, idempotent retries). Nothing leaves the browser.
 */
export class DemoBackend implements CatalogSource, ReservationSource {
  readonly mode = "demo" as const;
  private readonly loadedAt = Date.now();
  private readonly stock = new Map<string, DemoStock>();
  private readonly reservedPerEvent = new Map<string, number>();
  private readonly byIdempotencyKey = new Map<string, ReservationResponse>();

  constructor() {
    for (const event of DEMO_EVENTS) {
      event.tiers.forEach((tier, index) =>
        this.stock.set(ticketTypeId(event, index), { total: tier.total, initiallyAvailable: tier.available, available: tier.available }),
      );
    }
  }

  event(eventId: string): Promise<EventResponse> {
    return this.respond(() => this.toEvent(this.find(eventId)));
  }

  eventsOnSale(): Promise<EventResponse[]> {
    return this.respond(() => DEMO_EVENTS.filter((event) => event.status === "ON_SALE").map((event) => this.toEvent(event)));
  }

  upcomingEvents(): Promise<EventResponse[]> {
    return this.respond(() =>
      DEMO_EVENTS.filter((event) => event.status === "UPCOMING")
        .sort((a, b) => a.saleStartsIn - b.saleStartsIn)
        .map((event) => this.toEvent(event)),
    );
  }

  ticketTypes(eventId: string): Promise<TicketTypeResponse[]> {
    return this.respond(() => {
      const event = this.find(eventId);
      return event.tiers.map((tier, index) => ({
        id: ticketTypeId(event, index),
        name: tier.name,
        price: tier.price,
        totalQuantity: tier.total,
        inventoryStatus: "READY" as const,
        eventId: event.id,
        createdAt: this.at(-7 * DAY),
        updatedAt: this.at(-7 * DAY),
      }));
    });
  }

  inventory(eventId: string): Promise<InventoryResponse[]> {
    return this.respond(() => {
      const event = this.find(eventId);
      return event.tiers.map((_, index) => {
        const id = ticketTypeId(event, index);
        const stock = this.stock.get(id)!;
        const reservedEarlier = Math.min(stock.total - stock.initiallyAvailable, Math.round(stock.total * 0.05));
        const reserved = reservedEarlier + (stock.initiallyAvailable - stock.available);
        return {
          id,
          eventId: event.id,
          ticketTypeId: id,
          totalQuantity: stock.total,
          availableQuantity: stock.available,
          reservedQuantity: reserved,
          soldQuantity: stock.total - stock.available - reserved,
          updatedAt: new Date().toISOString(),
        };
      });
    });
  }

  reserve(request: ReservationRequest, idempotencyKey: string): Promise<ReservationResponse> {
    return this.respond(() => {
      const replay = this.byIdempotencyKey.get(idempotencyKey);
      if (replay) return replay;

      const event = this.find(request.eventId);
      if (salePhase(this.toEvent(event)) !== "open") {
        throw new AppError("sale-closed", "This sale is not open.");
      }
      const index = event.tiers.findIndex((_, i) => ticketTypeId(event, i) === request.ticketTypeId);
      if (index < 0) throw new AppError("not-found", "This ticket type does not exist.");
      const alreadyReserved = this.reservedPerEvent.get(event.id) ?? 0;
      if (alreadyReserved + request.quantity > PURCHASE_LIMIT) {
        throw new AppError("purchase-limit", `You can reserve up to ${PURCHASE_LIMIT} tickets for this event.`);
      }
      const stock = this.stock.get(request.ticketTypeId)!;
      if (stock.available < request.quantity) {
        throw new AppError("sold-out", "Requested inventory is no longer available.");
      }

      stock.available -= request.quantity;
      this.reservedPerEvent.set(event.id, alreadyReserved + request.quantity);
      const now = Date.now();
      const unitPrice = event.tiers[index].price;
      const reservation: ReservationResponse = {
        reservationId: `demo-res-${crypto.randomUUID().slice(0, 8)}`,
        eventId: event.id,
        ticketTypeId: request.ticketTypeId,
        userId: "demo-user",
        quantity: request.quantity,
        status: "ACTIVE",
        createdAt: new Date(now).toISOString(),
        updatedAt: new Date(now).toISOString(),
        expiresAt: new Date(now + HOLD_MS).toISOString(),
        unitPrice,
        amount: unitPrice * request.quantity,
      };
      this.byIdempotencyKey.set(idempotencyKey, reservation);
      return reservation;
    });
  }

  private find(eventId: string): DemoEvent {
    const event = DEMO_EVENTS.find((candidate) => candidate.id === eventId);
    if (!event) throw new AppError("not-found", "This event does not exist.");
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

  /** Runs after a short delay like a network call, so loading states show and errors arrive as rejections. */
  private respond<T>(produce: () => T): Promise<T> {
    return new Promise((resolve, reject) =>
      setTimeout(() => {
        try {
          resolve(produce());
        } catch (error) {
          reject(error);
        }
      }, SIMULATED_LATENCY_MS),
    );
  }
}

const ticketTypeId = (event: DemoEvent, index: number) => `${event.id}-tt${index + 1}`;
