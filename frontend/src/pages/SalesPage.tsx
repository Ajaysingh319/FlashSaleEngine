import { useSearchParams } from "react-router-dom";
import { SaleSection } from "../components/home/SaleSection";
import type { SaleSummary } from "../data/saleSummary";
import type { AsyncState } from "../hooks/useAsync";
import { useSales } from "../hooks/useSales";

function matching(state: AsyncState<SaleSummary[]>, query: string): AsyncState<SaleSummary[]> {
  if (state.status !== "success" || !query) return state;
  const needle = query.toLowerCase();
  const data = state.data.filter(({ event }) =>
    [event.name, event.city, event.venue].some((text) => text.toLowerCase().includes(needle)),
  );
  return { status: "success", data };
}

/** Every live and upcoming sale, optionally filtered by the header search (?q=). */
export function SalesPage() {
  const [params] = useSearchParams();
  const query = params.get("q")?.trim() ?? "";
  const { live, upcoming } = useSales();

  return (
    <div className="pb-4">
      <SaleSection
        id="flash-sales"
        title={query ? `Results for “${query}”` : "All Flash Sales"}
        subtitle={query ? "Live sales matching your search" : "Closing soonest first"}
        state={matching(live, query)}
        empty={{
          title: query ? "No live sales match your search" : "No live sales right now",
          message: query ? "Try another event name or city." : "New drops appear here the moment their sale opens.",
        }}
        onRetry={live.retry}
      />
      <SaleSection id="upcoming" title="Upcoming Sales" subtitle="Opening soon" state={matching(upcoming, query)} upcoming empty={null} onRetry={upcoming.retry} />
    </div>
  );
}
