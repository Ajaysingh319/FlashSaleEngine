import { Minus, Plus } from "lucide-react";

export function QuantitySelector({ value, max, onChange, disabled = false }: {
  value: number;
  max: number;
  onChange: (value: number) => void;
  disabled?: boolean;
}) {
  const button = "grid size-11 place-items-center rounded-full transition hover:bg-brand-soft disabled:cursor-not-allowed disabled:opacity-40";
  return (
    <div className="inline-flex items-center rounded-full border border-line bg-white" role="group" aria-label="Quantity">
      <button type="button" className={button} onClick={() => onChange(value - 1)} disabled={disabled || value <= 1} aria-label="Fewer tickets">
        <Minus className="size-4" aria-hidden />
      </button>
      <output className="w-8 text-center font-bold tabular-nums" aria-live="polite">{value}</output>
      <button type="button" className={button} onClick={() => onChange(value + 1)} disabled={disabled || value >= max} aria-label="More tickets">
        <Plus className="size-4" aria-hidden />
      </button>
    </div>
  );
}
