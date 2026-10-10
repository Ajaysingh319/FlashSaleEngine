export function Skeleton({ className = "" }: { className?: string }) {
  return <div className={`animate-pulse rounded-xl bg-brand-soft ${className}`} aria-hidden />;
}

export function SaleCardSkeleton() {
  return (
    <div className="overflow-hidden rounded-3xl border border-line bg-white p-3 shadow-card" aria-hidden>
      <Skeleton className="aspect-[4/3] rounded-2xl" />
      <div className="space-y-3 p-2 pt-4">
        <Skeleton className="h-5 w-4/5" />
        <Skeleton className="h-4 w-1/2" />
        <Skeleton className="h-6 w-1/3" />
        <Skeleton className="h-11 w-full rounded-full" />
      </div>
    </div>
  );
}
