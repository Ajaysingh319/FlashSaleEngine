import { config } from "../config/env";
import { DemoBackend } from "../demo/demoBackend";
import type { CatalogSource } from "./CatalogSource";
import { AppError } from "./errors";
import type { ReservationSource } from "./ReservationSource";

/** Until the API sources exist, api mode fails visibly instead of silently showing demo data. */
class NotYetConnected implements CatalogSource, ReservationSource {
  readonly mode = "api" as const;
  private fail(): Promise<never> {
    return Promise.reject(new AppError("unknown", "API mode is connected in Stage 5. Set VITE_DATA_MODE=demo for now."));
  }
  event() { return this.fail(); }
  eventsOnSale() { return this.fail(); }
  upcomingEvents() { return this.fail(); }
  ticketTypes() { return this.fail(); }
  inventory() { return this.fail(); }
  reserve() { return this.fail(); }
}

const backend = config.dataMode === "demo" ? new DemoBackend() : new NotYetConnected();

/** The one place that chooses where data comes from; everything else depends only on the interfaces. */
export const catalog: CatalogSource = backend;
export const reservations: ReservationSource = backend;
