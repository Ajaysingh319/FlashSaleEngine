import { ApiCatalogSource } from "../api/ApiCatalogSource";
import { ApiOrderSource } from "../api/ApiOrderSource";
import { ApiReservationSource } from "../api/ApiReservationSource";
import { http } from "../api/http";
import { config } from "../config/env";
import { DemoBackend } from "../demo/demoBackend";
import type { CatalogSource } from "./CatalogSource";
import type { OrderSource } from "./OrderSource";
import type { ReservationSource } from "./ReservationSource";

interface Sources {
  catalog: CatalogSource;
  reservations: ReservationSource;
  orders: OrderSource;
}

/** The one place that chooses where data comes from; everything else depends only on the interfaces. */
function createSources(): Sources {
  if (config.dataMode === "demo") {
    const demo = new DemoBackend();
    return { catalog: demo, reservations: demo, orders: demo };
  }
  return { catalog: new ApiCatalogSource(http), reservations: new ApiReservationSource(http), orders: new ApiOrderSource(http) };
}

export const { catalog, reservations, orders } = createSources();
