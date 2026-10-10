import { useEffect, useState } from "react";

export interface CountdownParts {
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
  isOver: boolean;
}

function partsUntil(targetMs: number): CountdownParts {
  const remaining = Math.max(0, targetMs - Date.now());
  const totalSeconds = Math.floor(remaining / 1000);
  return {
    days: Math.floor(totalSeconds / 86_400),
    hours: Math.floor((totalSeconds % 86_400) / 3_600),
    minutes: Math.floor((totalSeconds % 3_600) / 60),
    seconds: totalSeconds % 60,
    isOver: remaining === 0,
  };
}

/** Live time left until an ISO instant, refreshed every second until it is over. */
export function useCountdown(targetIso: string): CountdownParts {
  const targetMs = Date.parse(targetIso);
  const [parts, setParts] = useState(() => partsUntil(targetMs));

  useEffect(() => {
    setParts(partsUntil(targetMs));
    const timer = window.setInterval(() => {
      const next = partsUntil(targetMs);
      setParts(next);
      if (next.isOver) window.clearInterval(timer);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [targetMs]);

  return parts;
}
