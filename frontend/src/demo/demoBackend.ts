import type { CatalogSource } from "../data/CatalogSource";
import { AppError } from "../data/errors";
import type { OrderSource } from "../data/OrderSource";
import type { ReservationSource } from "../data/ReservationSource";
import { salePhase } from "../data/saleWindow";
import type { EventResponse, EventStatus, InventoryResponse, TicketTypeResponse } from "../types/catalog";
import type { OrderResponse, PaymentInitiationResponse, PaymentMethod } from "../types/order";
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
    id: "neon-skyline",
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
    id: "monsoon-beats",
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
    id: "champions-night",
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
    id: "comedy-gala",
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
    id: "symphony-stars",
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
    id: "indie-premiere",
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
/** How long the simulated payment provider takes to answer. */
const PAYMENT_DELAY_MS = 2_500;

interface DemoStock {
  total: number;
  initiallyAvailable: number;
  available: number;
  /** Tickets sold through demo checkouts (moved from reserved to sold). */
  soldInDemo: number;
}

/**
 * In-memory stand-in for the backend: serves the catalog, reservations, orders and payments against demo stock with
 * the same rules (sale window, stock, 4 per person per event, 10-minute holds, idempotent retries, asynchronous
 * payment that confirms or releases the tickets). Nothing leaves the browser.
 */
export class DemoBackend implements CatalogSource, ReservationSource, OrderSource {
  readonly mode = "demo" as const;
  private readonly loadedAt = Date.now();
  private readonly stock = new Map<string, DemoStock>();
  private readonly reservations = new Map<string, ReservationResponse>();
  private readonly orders = new Map<string, OrderResponse>();
  private readonly byIdempotencyKey = new Map<string, ReservationResponse | OrderResponse>();
  private readonly paymentIds = new Map<string, string>();

  constructor() {
    for (const event of DEMO_EVENTS) {
      event.tiers.forEach((tier, index) =>
        this.stock.set(ticketTypeId(event, index), {
          total: tier.total,
          initiallyAvailable: tier.available,
          available: tier.available,
          soldInDemo: 0,
        }),
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
        const reserved = reservedEarlier + (stock.initiallyAvailable - stock.available - stock.soldInDemo);
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
      if (replay) return { ...(replay as ReservationResponse) };
      this.expireLapsedHolds();

      const event = this.find(request.eventId);
      if (salePhase(this.toEvent(event)) !== "open") {
        throw new AppError("sale-closed", "This sale is not open.");
      }
      const index = event.tiers.findIndex((_, i) => ticketTypeId(event, i) === request.ticketTypeId);
      if (index < 0) throw new AppError("not-found", "This ticket type does not exist.");
      const alreadyReserved = [...this.reservations.values()]
        .filter((held) => held.eventId === event.id && (held.status === "ACTIVE" || held.status === "CONFIRMED"))
        .reduce((sum, held) => sum + held.quantity, 0);
      if (alreadyReserved + request.quantity > PURCHASE_LIMIT) {
        throw new AppError("purchase-limit", `You can reserve up to ${PURCHASE_LIMIT} tickets for this event.`);
      }
      const stock = this.stock.get(request.ticketTypeId)!;
      if (stock.available < request.quantity) {
        throw new AppError("sold-out", "Requested inventory is no longer available.");
      }

      stock.available -= request.quantity;
      const now = Date.now();
      const unitPrice = event.tiers[index].price;
      const reservation: ReservationResponse = {
        reservationId: objectId(),
        eventId: event.id,
        ticketTypeId: request.ticketTypeId,
        userId: "sample-user",
        quantity: request.quantity,
        status: "ACTIVE",
        createdAt: new Date(now).toISOString(),
        updatedAt: new Date(now).toISOString(),
        expiresAt: new Date(now + HOLD_MS).toISOString(),
        unitPrice,
        amount: unitPrice * request.quantity,
      };
      this.reservations.set(reservation.reservationId, reservation);
      this.byIdempotencyKey.set(idempotencyKey, reservation);
      return { ...reservation };
    });
  }

  reservation(reservationId: string): Promise<ReservationResponse> {
    return this.respond(() => {
      this.expireLapsedHolds();
      return { ...this.findReservation(reservationId) };
    });
  }

  myReservations(): Promise<ReservationResponse[]> {
    return this.respond(() => {
      this.expireLapsedHolds();
      return newestFirst([...this.reservations.values()]).map((reservation) => ({ ...reservation }));
    });
  }

  cancel(reservationId: string): Promise<void> {
    return this.respond(() => {
      this.expireLapsedHolds();
      const reservation = this.findReservation(reservationId);
      if (reservation.status !== "ACTIVE") throw new AppError("conflict", "Only an active reservation can be cancelled.");
      if (this.orderFor(reservationId)) throw new AppError("conflict", "This reservation has an order; cancel the order instead.");
      this.release(reservation, "CANCELLED");
    });
  }

  createOrder(reservationId: string, idempotencyKey: string): Promise<OrderResponse> {
    return this.respond(() => {
      const replay = this.byIdempotencyKey.get(idempotencyKey);
      if (replay) return { ...(replay as OrderResponse) };
      this.expireLapsedHolds();
      const reservation = this.findReservation(reservationId);
      if (reservation.status === "EXPIRED") throw new AppError("expired", "This reservation has expired.");
      if (reservation.status !== "ACTIVE") throw new AppError("invalid", "This reservation is no longer active.");
      if (this.orderFor(reservationId)) throw new AppError("conflict", "This reservation already has an order.");

      const now = new Date().toISOString();
      const order: OrderResponse = {
        orderId: objectId(),
        userId: reservation.userId,
        reservationId,
        eventId: reservation.eventId,
        ticketTypeId: reservation.ticketTypeId,
        quantity: reservation.quantity,
        unitPrice: reservation.unitPrice,
        totalAmount: reservation.amount,
        status: "PENDING_PAYMENT",
        paymentStatus: "PENDING",
        createdAt: now,
        updatedAt: now,
      };
      this.orders.set(order.orderId, order);
      this.byIdempotencyKey.set(idempotencyKey, order);
      return { ...order };
    });
  }

  order(orderId: string): Promise<OrderResponse> {
    return this.respond(() => {
      this.expireLapsedHolds();
      return { ...this.findOrder(orderId) };
    });
  }

  myOrders(): Promise<OrderResponse[]> {
    return this.respond(() => {
      this.expireLapsedHolds();
      return newestFirst([...this.orders.values()]).map((order) => ({ ...order }));
    });
  }

  cancelOrder(orderId: string): Promise<OrderResponse> {
    return this.respond(() => {
      this.expireLapsedHolds();
      const order = this.findOrder(orderId);
      if (order.status !== "PENDING_PAYMENT" || order.paymentStatus !== "PENDING") {
        throw new AppError("conflict", "Only an order awaiting payment can be cancelled.");
      }
      this.settleOrder(order, "CANCELLED", "PENDING");
      this.release(this.findReservation(order.reservationId), "CANCELLED");
      return { ...order };
    });
  }

  pay(orderId: string, method: PaymentMethod, _idempotencyKey: string): Promise<PaymentInitiationResponse> {
    return this.respond(() => {
      this.expireLapsedHolds();
      const order = this.findOrder(orderId);
      // One payment per order, like Order Service: asking again returns the payment already started.
      if (order.paymentStatus !== "PENDING") return { paymentId: this.paymentIdOf(order), orderId, status: order.paymentStatus };
      if (order.status === "EXPIRED") throw new AppError("expired", "This order expired before it was paid.");
      if (order.status !== "PENDING_PAYMENT") throw new AppError("conflict", "This order can no longer be paid.");

      this.settleOrder(order, "PENDING_PAYMENT", "PROCESSING");
      setTimeout(() => this.settlePayment(order.orderId, method), PAYMENT_DELAY_MS);
      return { paymentId: this.paymentIdOf(order), orderId, status: "PROCESSING" };
    });
  }

  /** The simulated provider's answer: success sells the tickets; a decline or timeout releases them (TDD 35-36). */
  private settlePayment(orderId: string, method: PaymentMethod): void {
    const order = this.orders.get(orderId);
    if (!order || order.paymentStatus !== "PROCESSING") return;
    const reservation = this.findReservation(order.reservationId);
    if (method === "MOCK_CARD") {
      reservation.status = "CONFIRMED";
      reservation.updatedAt = new Date().toISOString();
      this.stock.get(reservation.ticketTypeId)!.soldInDemo += reservation.quantity;
      this.settleOrder(order, "CONFIRMED", "SUCCEEDED");
    } else {
      this.settleOrder(order, "PAYMENT_FAILED", "FAILED");
      this.release(reservation, "CANCELLED");
    }
  }

  /** Holds past their 10 minutes expire (unless their payment is already in progress) and return to stock. */
  private expireLapsedHolds(): void {
    const now = Date.now();
    for (const reservation of this.reservations.values()) {
      if (reservation.status !== "ACTIVE" || Date.parse(reservation.expiresAt) > now) continue;
      const order = this.orderFor(reservation.reservationId);
      if (order?.paymentStatus === "PROCESSING") continue;
      if (order?.status === "PENDING_PAYMENT") this.settleOrder(order, "EXPIRED", order.paymentStatus);
      this.release(reservation, "EXPIRED");
    }
  }

  private release(reservation: ReservationResponse, status: "CANCELLED" | "EXPIRED"): void {
    reservation.status = status;
    reservation.updatedAt = new Date().toISOString();
    this.stock.get(reservation.ticketTypeId)!.available += reservation.quantity;
  }

  private settleOrder(order: OrderResponse, status: OrderResponse["status"], paymentStatus: OrderResponse["paymentStatus"]): void {
    order.status = status;
    order.paymentStatus = paymentStatus;
    order.updatedAt = new Date().toISOString();
  }

  /** One payment per order, so the order's payment ID never changes. */
  private paymentIdOf(order: OrderResponse): string {
    if (!this.paymentIds.has(order.orderId)) this.paymentIds.set(order.orderId, objectId());
    return this.paymentIds.get(order.orderId)!;
  }

  private orderFor(reservationId: string): OrderResponse | undefined {
    return [...this.orders.values()].find((order) => order.reservationId === reservationId);
  }

  private findReservation(reservationId: string): ReservationResponse {
    const reservation = this.reservations.get(reservationId);
    if (!reservation) throw new AppError("not-found", "This reservation does not exist.");
    return reservation;
  }

  private findOrder(orderId: string): OrderResponse {
    const order = this.orders.get(orderId);
    if (!order) throw new AppError("not-found", "This order does not exist.");
    return order;
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

/** A 24-hex-digit ID shaped like the MongoDB IDs the real backend returns. */
const objectId = () => Array.from(crypto.getRandomValues(new Uint8Array(12)), (byte) => byte.toString(16).padStart(2, "0")).join("");

const newestFirst = <T extends { createdAt: string }>(items: T[]) =>
  [...items].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
