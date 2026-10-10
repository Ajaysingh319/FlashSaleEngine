import { Timer } from "lucide-react";
import { useCountdown } from "../../hooks/useCountdown";
import { pad2 } from "../../utils/format";

/** Large white segmented countdown used in the hero (days / hours / minutes / seconds). */
export function CountdownPanel({ target, label }: { target: string; label: string }) {
  const { days, hours, minutes, seconds, isOver } = useCountdown(target);
  if (isOver) {
    return <p className="rounded-2xl bg-white/15 px-5 py-4 font-semibold">This sale has ended.</p>;
  }
  const segments = [
    [days, "Days"],
    [hours, "Hrs"],
    [minutes, "Mins"],
    [seconds, "Sec"],
  ] as const;
  return (
    <div>
      <p className="mb-2 text-xs font-semibold tracking-widest text-white/75 uppercase">{label}</p>
      <div className="inline-flex items-center gap-1 rounded-2xl bg-white px-3 py-3 text-brand shadow-lift sm:gap-2 sm:px-5" role="timer" aria-live="off">
        {segments.map(([value, unit], index) => (
          <div key={unit} className="flex items-center gap-1 sm:gap-2">
            {index > 0 && <span className="pb-4 text-2xl font-bold text-brand/50" aria-hidden>:</span>}
            <div className="min-w-12 text-center sm:min-w-14">
              <div className="text-3xl font-extrabold tabular-nums sm:text-4xl">{pad2(value)}</div>
              <div className="text-[10px] font-semibold tracking-widest text-ink-muted uppercase">{unit}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/** Compact pill for cards, e.g. "Ends in 05h 12m 03s". */
export function CountdownChip({ target, prefix }: { target: string; prefix: string }) {
  const { days, hours, minutes, seconds, isOver } = useCountdown(target);
  const text = isOver
    ? "Ended"
    : days > 0
      ? `${prefix} ${days}d ${pad2(hours)}h ${pad2(minutes)}m`
      : `${prefix} ${pad2(hours)}h ${pad2(minutes)}m ${pad2(seconds)}s`;
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-urgent px-3 py-1.5 text-xs font-semibold text-white shadow-lg tabular-nums">
      <Timer className="size-3.5" aria-hidden /> {text}
    </span>
  );
}

/** Red "Hurry up" panel for the details page, after the reference product page. */
export function UrgencyCountdown({ target, title, subtitle }: { target: string; title: string; subtitle: string }) {
  const { days, hours, minutes, seconds, isOver } = useCountdown(target);
  if (isOver) return null;
  const segments = [
    [days, "Days"],
    [hours, "Hours"],
    [minutes, "Mins"],
    [seconds, "Secs"],
  ] as const;
  return (
    <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-urgent/20 bg-urgent-soft px-4 py-3" role="timer">
      <div className="flex items-center gap-3">
        <Timer className="size-6 text-urgent" aria-hidden />
        <div>
          <p className="font-bold text-urgent">{title}</p>
          <p className="text-[10px] font-semibold tracking-widest text-ink-muted uppercase">{subtitle}</p>
        </div>
      </div>
      <div className="flex items-center gap-1.5">
        {segments.map(([value, unit], index) => (
          <div key={unit} className="flex items-center gap-1.5">
            {index > 0 && <span className="font-bold text-urgent/60" aria-hidden>:</span>}
            <div className="min-w-11 rounded-lg bg-white px-1.5 py-1 text-center shadow-sm">
              <div className="font-extrabold text-ink tabular-nums">{pad2(value)}</div>
              <div className="text-[9px] font-semibold text-ink-muted uppercase">{unit}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
