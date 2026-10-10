import type { InventoryResponse, TicketTypeResponse } from "../../types/catalog";
import { formatMoney } from "../../utils/format";

/** Ticket tiers as selectable options (the "variants" of a drop); a tier with no stock left cannot be picked. */
export function TicketTypePicker({ ticketTypes, inventory, selectedId, onSelect, disabled }: {
  ticketTypes: TicketTypeResponse[];
  inventory: InventoryResponse[] | null;
  selectedId: string | null;
  onSelect: (id: string) => void;
  disabled: boolean;
}) {
  return (
    <fieldset>
      <legend className="mb-2 text-sm font-semibold">Ticket type</legend>
      <div className="grid gap-2 sm:grid-cols-2">
        {ticketTypes.map((type) => {
          const available = inventory?.find((item) => item.ticketTypeId === type.id)?.availableQuantity;
          const soldOut = available === 0;
          const selected = type.id === selectedId;
          return (
            <button
              key={type.id}
              type="button"
              onClick={() => onSelect(type.id)}
              disabled={disabled || soldOut}
              aria-pressed={selected}
              className={`flex items-center justify-between rounded-2xl border-2 px-4 py-3 text-left transition disabled:cursor-not-allowed disabled:opacity-50 ${
                selected ? "border-brand bg-brand-soft" : "border-line hover:border-brand/40"
              }`}
            >
              <span>
                <span className="block font-semibold">{type.name}</span>
                <span className="text-xs text-ink-muted">{soldOut ? "Sold out" : available !== undefined ? `${available} left` : " "}</span>
              </span>
              <span className="font-bold text-brand">{formatMoney(type.price)}</span>
            </button>
          );
        })}
      </div>
    </fieldset>
  );
}
