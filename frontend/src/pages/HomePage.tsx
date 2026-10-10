import { ArrowRight } from "lucide-react";
import { Link } from "react-router-dom";
import { Hero } from "../components/home/Hero";
import { PromoBanners } from "../components/home/PromoBanners";
import { SaleSection } from "../components/home/SaleSection";
import { pickFeatured } from "../data/saleSummary";
import { useSales } from "../hooks/useSales";

export function HomePage() {
  const { live, upcoming } = useSales();
  const featured = live.status === "success" ? pickFeatured(live.data) : null;
  const heroStatus = live.status === "loading" ? "loading" : live.status === "error" ? "error" : "ready";

  return (
    <>
      <Hero featured={featured} status={heroStatus} />
      <PromoBanners />
      <SaleSection
        id="flash-sales"
        title="Live Flash Sales"
        subtitle="Closing soonest first"
        action={
          <Link to="/sales" className="inline-flex items-center gap-1 font-semibold text-brand hover:text-brand-deep">
            View all <ArrowRight className="size-4" aria-hidden />
          </Link>
        }
        state={live}
        limit={8}
        empty={{ title: "No live sales right now", message: "New drops appear here the moment their sale opens." }}
        onRetry={live.retry}
      />
      <SaleSection id="upcoming" title="Upcoming Sales" subtitle="Opening soon" state={upcoming} upcoming limit={4} empty={null} onRetry={upcoming.retry} />
    </>
  );
}
