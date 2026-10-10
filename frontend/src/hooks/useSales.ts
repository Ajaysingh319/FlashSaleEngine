import { catalog } from "../data/sources";
import { loadSummaries } from "../data/saleSummary";
import { useAsync } from "./useAsync";

const bySaleEnd = (a: { event: { saleEndTime: string } }, b: { event: { saleEndTime: string } }) =>
  Date.parse(a.event.saleEndTime) - Date.parse(b.event.saleEndTime);

/** Live sales (closing soonest first) and upcoming sales, each with prices and stock from the active source. */
export function useSales() {
  const live = useAsync(async () => (await loadSummaries(catalog, await catalog.eventsOnSale())).sort(bySaleEnd), []);
  const upcoming = useAsync(async () => loadSummaries(catalog, await catalog.upcomingEvents()), []);
  return { live, upcoming };
}
