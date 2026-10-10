import type { EventResponse } from "../../types/catalog";
import { PHOTOS, themeFor } from "./eventArtwork";

/** An event's image: a real photo when one is registered, otherwise its themed illustration. */
export function EventArt({ event, className = "", large = false }: {
  event: EventResponse | null;
  className?: string;
  large?: boolean;
}) {
  const photo = event ? PHOTOS[event.id] : undefined;
  if (photo) {
    return <img src={photo} alt={event?.name ?? ""} className={`object-cover ${className}`} loading="lazy" />;
  }
  const theme = themeFor(event);
  const Icon = theme.icon;
  return (
    <div
      role="img"
      aria-label={event ? `${theme.label}: ${event.name}` : "Featured drop"}
      className={`relative isolate overflow-hidden bg-linear-to-br ${theme.gradient} ${className}`}
    >
      <div className="absolute -top-1/4 -right-1/4 size-3/4 rounded-full bg-white/15 blur-2xl" aria-hidden />
      <div className="absolute -bottom-1/3 -left-1/4 size-2/3 rounded-full bg-black/10 blur-2xl" aria-hidden />
      <div className="absolute top-[14%] left-[10%] size-10 rotate-12 rounded-xl border-2 border-white/30" aria-hidden />
      <div className="absolute right-[12%] bottom-[16%] size-6 rounded-full border-2 border-white/40" aria-hidden />
      <div className="absolute inset-0 grid place-items-center">
        <div className={`grid place-items-center rounded-[28%] bg-white/15 shadow-2xl ring-1 ring-white/30 backdrop-blur-sm ${large ? "size-40 sm:size-48" : "size-20"}`}>
          <Icon className={`text-white drop-shadow ${large ? "size-20 sm:size-24" : "size-10"}`} strokeWidth={1.6} aria-hidden />
        </div>
      </div>
    </div>
  );
}
