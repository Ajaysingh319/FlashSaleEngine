import { useEffect, useState } from "react";
import { catalog } from "../data/sources";
import type { EventResponse } from "../types/catalog";

/** Events already fetched this session, so list pages do not ask twice for the same one. */
const cache = new Map<string, Promise<EventResponse | null>>();

function loadEvent(eventId: string): Promise<EventResponse | null> {
  if (!cache.has(eventId)) cache.set(eventId, catalog.event(eventId).catch(() => null));
  return cache.get(eventId)!;
}

/** Event details for the given IDs (orders and reservations only carry the eventId). Missing ones stay absent. */
export function useEventLookup(eventIds: string[]): Map<string, EventResponse> {
  const [events, setEvents] = useState(new Map<string, EventResponse>());
  const key = [...new Set(eventIds)].sort().join(",");

  useEffect(() => {
    let cancelled = false;
    const ids = key ? key.split(",") : [];
    Promise.all(ids.map(loadEvent)).then((loaded) => {
      if (cancelled) return;
      setEvents(new Map(loaded.filter((event): event is EventResponse => event !== null).map((event) => [event.id, event])));
    });
    return () => {
      cancelled = true;
    };
  }, [key]);

  return events;
}
