import { ApiCatalogSource } from "../api/ApiCatalogSource";
import { ApiReservationSource } from "../api/ApiReservationSource";
import { http } from "../api/http";
import { config } from "../config/env";
import { DemoBackend } from "../demo/demoBackend";
import type { CatalogSource } from "./CatalogSource";
import type { ReservationSource } from "./ReservationSource";

/** The one place that chooses where data comes from; everything else depends only on the interfaces. */
function createSources(): { catalog: CatalogSource; reservations: ReservationSource } {
  if (config.dataMode === "demo") {
    const demo = new DemoBackend();
    return { catalog: demo, reservations: demo };
  }
  return { catalog: new ApiCatalogSource(http), reservations: new ApiReservationSource(http) };
}

export const { catalog, reservations } = createSources();
