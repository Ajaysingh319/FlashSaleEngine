import { config } from "../config/env";
import { DemoCatalogSource } from "../demo/demoCatalog";
import type { CatalogSource } from "./CatalogSource";

/** Until the API source exists, api mode fails visibly instead of silently showing demo data. */
class NotYetAvailableCatalogSource implements CatalogSource {
  readonly mode = "api" as const;
  private fail<T>(): Promise<T> {
    return Promise.reject(new Error("API mode is connected in Stage 5. Set VITE_DATA_MODE=demo for now."));
  }
  eventsOnSale() { return this.fail<never>(); }
  upcomingEvents() { return this.fail<never>(); }
  ticketTypes() { return this.fail<never>(); }
  inventory() { return this.fail<never>(); }
}

export const catalog: CatalogSource =
  config.dataMode === "demo" ? new DemoCatalogSource() : new NotYetAvailableCatalogSource();
