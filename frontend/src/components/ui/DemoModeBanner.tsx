import { FlaskConical } from "lucide-react";
import { config } from "../../config/env";

/** Shown on every page in demo mode, so sample data is never mistaken for real sales. */
export function DemoModeBanner() {
  if (config.dataMode !== "demo") return null;
  return (
    <div className="bg-ink px-4 py-2 text-center text-xs font-medium text-white">
      <FlaskConical className="mr-1.5 inline size-3.5 align-[-2px]" aria-hidden />
      Demo mode: sample events and stock for previewing the design. No real tickets are reserved.
    </div>
  );
}
