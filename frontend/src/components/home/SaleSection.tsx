import type { ReactNode } from "react";
import type { AsyncState } from "../../hooks/useAsync";
import type { SaleSummary } from "../../data/saleSummary";
import { SaleCard } from "../sale/SaleCard";
import { SaleCardSkeleton } from "../ui/Skeleton";
import { EmptyState, ErrorState } from "../ui/StateMessage";

/** Centered pill heading with side rules, after the reference storefront's section titles. */
export function SectionHeading({ title, subtitle, action }: { title: string; subtitle?: string; action?: ReactNode }) {
  return (
    <div className="mb-8">
      <div className="flex items-center gap-4">
        <span className="h-px flex-1 bg-linear-to-r from-transparent to-line" aria-hidden />
        <h2 className="rounded-full border border-line bg-white px-6 py-2 text-lg font-extrabold shadow-card sm:text-xl">{title}</h2>
        <span className="h-px flex-1 bg-linear-to-l from-transparent to-line" aria-hidden />
      </div>
      {(subtitle || action) && (
        <div className="mt-3 flex flex-wrap items-center justify-center gap-x-4 gap-y-1 text-sm text-ink-muted">
          {subtitle && <p>{subtitle}</p>}
          {action}
        </div>
      )}
    </div>
  );
}

const GRID = "grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4";

export function SaleSection({ id, title, subtitle, action, state, upcoming = false, limit, empty, onRetry }: {
  id: string;
  title: string;
  subtitle?: string;
  action?: ReactNode;
  state: AsyncState<SaleSummary[]>;
  upcoming?: boolean;
  limit?: number;
  empty: { title: string; message: string } | null;
  onRetry: () => void;
}) {
  if (state.status === "success" && state.data.length === 0 && empty === null) return null;

  return (
    <section id={id} className="mx-auto max-w-7xl scroll-mt-24 px-4 pt-16 sm:px-6">
      <SectionHeading title={title} subtitle={subtitle} action={action} />
      {state.status === "loading" && (
        <div className={GRID}>{Array.from({ length: 4 }, (_, index) => <SaleCardSkeleton key={index} />)}</div>
      )}
      {state.status === "error" && (
        <ErrorState message={`Could not load ${title.toLowerCase()}: ${state.error.message}`} onRetry={onRetry} />
      )}
      {state.status === "success" && state.data.length === 0 && empty && <EmptyState {...empty} />}
      {state.status === "success" && state.data.length > 0 && (
        <div className={GRID}>
          {state.data.slice(0, limit).map((sale) => <SaleCard key={sale.event.id} sale={sale} upcoming={upcoming} />)}
        </div>
      )}
    </section>
  );
}
