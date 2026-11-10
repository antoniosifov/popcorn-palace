"use client";

import { useEffect, useRef } from "react";

import type { Seat, SeatCategory, SeatRow } from "@/lib/api/types";
import { formatMoney } from "@/lib/money";

/**
 * The hall, drawn as it is: the screen at the top, then every row from the
 * front to the back, each seat a mark whose look says whether it can be had.
 * Rows shorter than the widest one are centred, as they are in the room.
 */

export type SeatLook = "free" | "mine" | "held" | "booked";

export function seatLook(seat: Seat, picked: boolean): SeatLook {
  if (picked || seat.heldByMe) {
    return "mine";
  }
  if (seat.status === "AVAILABLE") {
    return "free";
  }
  return seat.status === "HELD" ? "held" : "booked";
}

function seatClass(category: SeatCategory, look: SeatLook): string {
  const classes = ["seat"];
  if (category === "VIP") {
    classes.push("seat-vip");
  }
  if (look !== "free") {
    classes.push(`seat-${look}`);
  }
  return classes.join(" ");
}

const LOOK_WORDS: Record<SeatLook, string> = {
  free: "available",
  mine: "yours",
  held: "being booked by someone else",
  booked: "sold",
};

type Props = {
  rows: SeatRow[];
  /** Seats the customer has picked but not held yet. */
  selected?: number[];
  currency: string;
  /** Called when a free or picked seat is clicked. Without it the hall is a picture, not a control. */
  onToggle?: (seatId: number) => void;
  /** Seats cannot be picked right now (the customer is paying, or a request is under way). */
  locked?: boolean;
};

export function SeatGrid({ rows, selected = [], currency, onToggle, locked = false }: Props) {
  const widest = Math.max(1, ...rows.map((row) => row.seats.length));
  const picked = new Set(selected);
  // Seats never grow past 34px and never shrink below 24px, the smallest target
  // a finger hits reliably. A hall too wide for a phone at that size scrolls
  // sideways inside its own frame, starting from the middle of the rows.
  const maxWidth = widest * 34 + (widest - 1) * 4 + 2 * 28;
  const minWidth = widest * 24 + (widest - 1) * 3 + 26;
  const frame = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const element = frame.current;
    if (element && element.scrollWidth > element.clientWidth) {
      element.scrollLeft = (element.scrollWidth - element.clientWidth) / 2;
    }
  }, [minWidth]);

  return (
    <div ref={frame} className="overflow-x-auto">
      <div className="mx-auto w-full" style={{ maxWidth, minWidth }}>
        <svg viewBox="0 0 400 26" className="mx-auto mb-1 block w-[86%]" aria-hidden="true" focusable="false">
          <path
            d="M4 22 Q200 -10 396 22"
            fill="none"
            stroke="var(--color-faint)"
            strokeWidth="2"
            strokeLinecap="round"
          />
        </svg>
        <p className="mb-5 text-center text-small text-faint">Screen</p>

        <div role="group" aria-label="Seats" className="grid gap-1 sm:gap-1.5">
          {rows.map((row) => {
            const offset = Math.floor((widest - row.seats.length) / 2);
            return (
              <div key={row.row} className="flex items-center gap-1.5 sm:gap-2">
                <span className="num w-5 flex-none text-right text-small text-faint" aria-hidden="true">
                  {row.row}
                </span>
                <div
                  className="grid flex-1 gap-[3px] sm:gap-1"
                  style={{ gridTemplateColumns: `repeat(${widest}, minmax(0, 1fr))` }}
                >
                  {row.seats.map((seat, index) => {
                    const look = seatLook(seat, picked.has(seat.seatId));
                    const label = `Row ${row.row}, seat ${seat.number}, ${seat.category === "VIP" ? "VIP" : "standard"}, ${formatMoney(seat.price, currency)}, ${LOOK_WORDS[look]}`;
                    const style = index === 0 ? { gridColumnStart: offset + 1 } : undefined;
                    const className = seatClass(seat.category, look);
                    // A seat the customer holds is theirs until they release the hold; it is not a toggle.
                    const clickable = onToggle && (look === "free" || (look === "mine" && !seat.heldByMe));
                    if (!clickable) {
                      return (
                        <span
                          key={seat.seatId}
                          role="img"
                          aria-label={label}
                          title={label}
                          className={className}
                          style={style}
                        />
                      );
                    }
                    return (
                      <button
                        key={seat.seatId}
                        type="button"
                        aria-label={label}
                        aria-pressed={look === "mine"}
                        title={label}
                        disabled={locked}
                        onClick={() => onToggle(seat.seatId)}
                        className={className}
                        style={style}
                      />
                    );
                  })}
                </div>
                <span className="num hidden w-5 flex-none text-small text-faint sm:block" aria-hidden="true">
                  {row.row}
                </span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

/** What each mark means, shown with the marks themselves, and what each kind of seat costs. */
export function SeatLegend({ rows, currency }: { rows: SeatRow[]; currency: string }) {
  const prices = new Map<SeatCategory, number>();
  for (const row of rows) {
    for (const seat of row.seats) {
      prices.set(seat.category, seat.price);
    }
  }
  const entries: { key: string; className: string; label: string; price?: number }[] = [];
  if (prices.has("STANDARD")) {
    entries.push({ key: "standard", className: "seat", label: "Standard", price: prices.get("STANDARD") });
  }
  if (prices.has("VIP")) {
    entries.push({ key: "vip", className: "seat seat-vip", label: "VIP", price: prices.get("VIP") });
  }
  entries.push(
    { key: "mine", className: "seat seat-mine", label: "Your seats" },
    { key: "held", className: "seat seat-held", label: "Being booked" },
    { key: "booked", className: "seat seat-booked", label: "Sold" },
  );
  return (
    <ul className="flex flex-wrap justify-center gap-x-5 gap-y-2 text-label text-muted">
      {entries.map((entry) => (
        <li key={entry.key} className="flex items-center gap-2">
          <span className={`${entry.className} w-[18px] flex-none`} aria-hidden="true" />
          <span>
            {entry.label}
            {entry.price !== undefined ? (
              <span className="num ml-1.5 text-ink">{formatMoney(entry.price, currency)}</span>
            ) : null}
          </span>
        </li>
      ))}
    </ul>
  );
}
