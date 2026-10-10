import { ArrowRight, MapPin, Sparkles } from "lucide-react";
import { Link } from "react-router-dom";
import type { SaleSummary } from "../../data/saleSummary";
import { formatMoney } from "../../utils/format";
import { CountdownPanel } from "../sale/Countdown";
import { EventArt } from "../sale/EventArt";
import { Skeleton } from "../ui/Skeleton";

/** Purple gradient hero featuring the live sale closing soonest, or a designed fallback when none is live. */
export type HeroStatus = "loading" | "ready" | "error";

export function Hero({ featured, status }: { featured: SaleSummary | null; status: HeroStatus }) {
  const loading = status === "loading";
  return (
    <section className="px-4 pt-6 sm:px-6">
      <div className="relative mx-auto max-w-7xl overflow-hidden rounded-[2rem] bg-linear-to-br from-brand via-[#6a2fe0] to-brand-deep px-6 pt-10 pb-20 text-white shadow-lift sm:rounded-[2.75rem] sm:px-12 sm:pt-14 sm:pb-24">
        <HeroDecoration />
        <div className="relative grid items-center gap-10 lg:grid-cols-[1.1fr_1fr]">
          <div>
            <span className="inline-flex items-center gap-2 rounded-full bg-white/15 px-3 py-1.5 text-xs font-semibold ring-1 ring-white/25">
              <Sparkles className="size-3.5" aria-hidden /> Limited ticket drops
            </span>
            <h1 className="mt-5 text-4xl leading-[1.05] font-extrabold tracking-tight sm:text-5xl lg:text-6xl">
              Big Deals.
              <br />
              <span className="text-[#d9c8ff]">Limited Stock.</span>
            </h1>
            <p className="mt-4 max-w-md text-base text-white/80 sm:text-lg">
              Concerts, festivals and match nights released in flash sales. Reserve fast; when the stock is gone, it is gone.
            </p>

            <div className="mt-8">
              {loading ? <FeaturedSkeleton /> : featured ? <FeaturedSale sale={featured} /> : <NoLiveSale unavailable={status === "error"} />}
            </div>
          </div>

          <div className="relative mx-auto w-full max-w-md lg:max-w-none">
            {loading ? (
              <Skeleton className="aspect-[4/3] rounded-[2rem] bg-white/15" />
            ) : (
              <EventArt event={featured?.event ?? null} large className="aspect-[4/3] w-full rounded-[2rem] shadow-2xl ring-1 ring-white/20" />
            )}
          </div>
        </div>
      </div>
    </section>
  );
}

function FeaturedSale({ sale }: { sale: SaleSummary }) {
  const { event, startingPrice } = sale;
  return (
    <div className="space-y-6">
      <div>
        <p className="text-xs font-semibold tracking-widest text-white/70 uppercase">Featured drop</p>
        <h2 className="mt-1 text-2xl font-bold sm:text-3xl">{event.name}</h2>
        <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-white/80">
          <span className="inline-flex items-center gap-1"><MapPin className="size-3.5" aria-hidden />{event.city} · {event.venue}</span>
          {startingPrice !== null && <span className="font-semibold text-white">From {formatMoney(startingPrice)}</span>}
        </p>
      </div>
      <CountdownPanel target={event.saleEndTime} label="Sale ends in" />
      <div className="flex flex-wrap gap-3">
        <a href="#flash-sales" className="inline-flex items-center gap-2 rounded-full bg-white px-6 py-3 text-sm font-bold text-brand shadow-lg transition hover:-translate-y-0.5 hover:shadow-xl">
          Shop Deals <ArrowRight className="size-4" aria-hidden />
        </a>
        <Link to={`/events/${encodeURIComponent(event.id)}`} className="rounded-full px-6 py-3 text-sm font-bold text-white ring-1 ring-white/50 transition hover:bg-white/10">
          View event
        </Link>
      </div>
    </div>
  );
}

/** No featured sale: either none is live, or live sales could not be loaded. Never shows a countdown. */
function NoLiveSale({ unavailable }: { unavailable: boolean }) {
  return (
    <div className="max-w-md rounded-3xl bg-white/10 p-6 ring-1 ring-white/20">
      <h2 className="text-xl font-bold">{unavailable ? "Live sales are unavailable right now" : "No flash sale is live right now"}</h2>
      <p className="mt-2 text-sm text-white/80">
        {unavailable
          ? "We could not load the current sales. Please try again in a moment."
          : "New drops are announced here first. Check the upcoming sales and come back when they open."}
      </p>
      <a href="#upcoming" className="mt-5 inline-flex items-center gap-2 rounded-full bg-white px-6 py-3 text-sm font-bold text-brand shadow-lg transition hover:-translate-y-0.5">
        See upcoming sales <ArrowRight className="size-4" aria-hidden />
      </a>
    </div>
  );
}

function FeaturedSkeleton() {
  return (
    <div className="space-y-4" aria-label="Loading featured sale">
      <Skeleton className="h-4 w-28 bg-white/20" />
      <Skeleton className="h-8 w-3/4 bg-white/20" />
      <Skeleton className="h-20 w-72 rounded-2xl bg-white/20" />
      <Skeleton className="h-12 w-40 rounded-full bg-white/20" />
    </div>
  );
}

/** Geometric shapes and a slide-dot column, after the reference storefront. */
function HeroDecoration() {
  return (
    <div className="pointer-events-none absolute inset-0" aria-hidden>
      <div className="absolute -top-24 -left-24 size-80 rotate-12 rounded-[4rem] bg-white/8" />
      <div className="absolute -right-16 -bottom-28 size-96 -rotate-12 rounded-[5rem] bg-white/6" />
      <div className="absolute top-1/3 left-1/2 size-72 rounded-full bg-fuchsia-400/20 blur-3xl" />
      <div className="absolute top-10 right-1/3 size-16 rotate-45 rounded-2xl border-2 border-white/20" />
      <div className="absolute top-1/2 right-5 hidden -translate-y-1/2 flex-col gap-2 sm:flex">
        {[0, 1, 2, 3].map((dot) => (
          <span key={dot} className={`size-2 rounded-full ${dot === 1 ? "bg-white" : "bg-white/40"}`} />
        ))}
      </div>
    </div>
  );
}
