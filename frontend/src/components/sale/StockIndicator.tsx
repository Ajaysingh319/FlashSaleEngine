/** Remaining-stock bar. Renders nothing when the API provides no inventory figures. */
export function StockIndicator({ available, total }: { available: number | null; total: number | null }) {
  if (available === null || total === null || total <= 0) return null;
  const share = Math.min(1, Math.max(0, available / total));
  const low = available > 0 && (available <= 10 || share <= 0.1);
  const message = available === 0 ? "Sold out" : low ? `Hurry, only ${available} left` : `${available} of ${total} left`;
  return (
    <div>
      <p className={`mb-1.5 text-xs font-medium ${available === 0 || low ? "text-urgent" : "text-ink-muted"}`}>{message}</p>
      <div className="h-1.5 overflow-hidden rounded-full bg-brand-soft" role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={available} aria-label="Tickets left">
        <div className={`h-full rounded-full transition-all ${low ? "bg-urgent" : "bg-brand"}`} style={{ width: `${share * 100}%` }} />
      </div>
    </div>
  );
}
