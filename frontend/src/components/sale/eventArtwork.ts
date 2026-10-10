import { Clapperboard, Mic, Music2, Piano, Tent, Ticket, Trophy, type LucideIcon } from "lucide-react";
import type { EventResponse } from "../../types/catalog";

/*
 * Presentation-only artwork. The backend stores no images, so each event is illustrated by a local theme chosen
 * from its name and description. To use real photos, put them in public/events/ and map event IDs in PHOTOS.
 */

export interface ArtworkTheme {
  gradient: string;
  icon: LucideIcon;
  label: string;
}

const THEMES = {
  concert: { gradient: "from-[#7138E8] via-[#a855f7] to-[#ff6fb5]", icon: Music2, label: "Live music" },
  festival: { gradient: "from-[#ff7a45] via-[#ff5f6d] to-[#a855f7]", icon: Tent, label: "Festival" },
  sports: { gradient: "from-[#0ea5e9] via-[#2563eb] to-[#5825C9]", icon: Trophy, label: "Sports" },
  comedy: { gradient: "from-[#f59e0b] via-[#f97316] to-[#D93650]", icon: Mic, label: "Comedy" },
  classical: { gradient: "from-[#1f1b2e] via-[#3b2a6b] to-[#7138E8]", icon: Piano, label: "Classical" },
  film: { gradient: "from-[#111827] via-[#4c1d95] to-[#D93650]", icon: Clapperboard, label: "Film" },
  general: { gradient: "from-[#5825C9] via-[#7138E8] to-[#22d3ee]", icon: Ticket, label: "Event" },
} satisfies Record<string, ArtworkTheme>;

const KEYWORDS: [RegExp, keyof typeof THEMES][] = [
  [/cricket|football|match|stadium|champion|league|marathon/i, "sports"],
  [/comedy|stand-up|standup/i, "comedy"],
  [/symphony|orchestra|classical|opera/i, "classical"],
  [/film|cinema|premiere|screening/i, "film"],
  [/festival|fest\b/i, "festival"],
  [/live|tour|concert|music|beats|dj/i, "concert"],
];

/** Optional real photos, keyed by event ID, e.g. { "64f0…": "/events/arena.jpg" }. */
export const PHOTOS: Record<string, string> = {};

export function themeFor(event: Pick<EventResponse, "name" | "description"> | null): ArtworkTheme {
  if (!event) return THEMES.general;
  const text = `${event.name} ${event.description ?? ""}`;
  const match = KEYWORDS.find(([pattern]) => pattern.test(text));
  return THEMES[match ? match[1] : "general"];
}
