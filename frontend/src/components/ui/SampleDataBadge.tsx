import { FlaskConical } from "lucide-react";
import { config } from "../../config/env";

/** The single, quiet marker shown when the app runs on built-in sample data instead of the backend. */
export function SampleDataBadge() {
  if (config.dataMode !== "demo") return null;
  return (
    <span
      className="hidden items-center gap-1 rounded-full bg-brand-soft px-2.5 py-1 text-[11px] font-semibold text-brand-deep sm:inline-flex"
      title="Running on built-in sample data. Set VITE_DATA_MODE=api to use the live backend."
    >
      <FlaskConical className="size-3" aria-hidden /> Sample data
    </span>
  );
}
