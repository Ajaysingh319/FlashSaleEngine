import { AlertTriangle, CalendarX2, RotateCcw } from "lucide-react";

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-center gap-3 rounded-3xl border border-urgent/20 bg-urgent-soft px-6 py-10 text-center">
      <AlertTriangle className="size-8 text-urgent" aria-hidden />
      <p className="max-w-md text-sm text-ink">{message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="inline-flex items-center gap-2 rounded-full bg-white px-4 py-2 text-sm font-semibold text-urgent shadow-card transition hover:shadow-lift"
        >
          <RotateCcw className="size-4" aria-hidden /> Try again
        </button>
      )}
    </div>
  );
}

export function EmptyState({ title, message }: { title: string; message: string }) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-3xl border border-dashed border-line bg-white px-6 py-12 text-center">
      <CalendarX2 className="size-8 text-brand" aria-hidden />
      <p className="font-semibold">{title}</p>
      <p className="max-w-md text-sm text-ink-muted">{message}</p>
    </div>
  );
}
