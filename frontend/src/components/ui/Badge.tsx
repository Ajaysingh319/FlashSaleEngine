import type { ReactNode } from "react";

const TONES = {
  live: "bg-success-soft text-success",
  urgent: "bg-urgent text-white",
  upcoming: "bg-brand-soft text-brand-deep",
  neutral: "bg-white/90 text-ink",
  muted: "bg-ink/80 text-white",
} as const;

export type BadgeTone = keyof typeof TONES;

export function Badge({ tone, children, pulse = false }: { tone: BadgeTone; children: ReactNode; pulse?: boolean }) {
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${TONES[tone]}`}>
      {pulse && <span className="size-1.5 animate-pulse rounded-full bg-current" aria-hidden />}
      {children}
    </span>
  );
}
