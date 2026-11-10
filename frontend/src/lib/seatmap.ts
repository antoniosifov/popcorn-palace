import { sumCents } from "./money";
import type { Seat, SeatEvent, SeatMap } from "./api/types";

/**
 * The seat map as the browser keeps it: loaded once, then changed by the
 * events of the live stream and by what the customer picks. Everything here
 * is a pure function, so the rules can be tested without a browser.
 */

/** Most seats one customer can hold for one showtime (the API's app.holds.max-seats). */
export const MAX_SEATS = 8;

/**
 * Applies one event of the live stream. The customer's own hold is told apart
 * from everybody else's by its id: the stream is public and the same for all.
 */
export function applySeatEvent(map: SeatMap, event: SeatEvent, myHoldId: string | null): SeatMap {
  if (event.showtimeId !== map.showtimeId) {
    return map;
  }
  const changed = new Set(event.seatIds);
  const heldByMe = event.status === "HELD" && myHoldId !== null && event.holdId === myHoldId;
  let touched = false;
  const rows = map.rows.map((row) => {
    if (!row.seats.some((seat) => changed.has(seat.seatId))) {
      return row;
    }
    return {
      ...row,
      seats: row.seats.map((seat) => {
        if (!changed.has(seat.seatId) || (seat.status === event.status && seat.heldByMe === heldByMe)) {
          return seat;
        }
        touched = true;
        return { ...seat, status: event.status, heldByMe };
      }),
    };
  });
  return touched ? withCounts({ ...map, rows }) : map;
}

/** Marks seats as held by the customer, right after their hold was created (before the event arrives). */
export function markHeldByMe(map: SeatMap, seatIds: number[]): SeatMap {
  const mine = new Set(seatIds);
  return withCounts({
    ...map,
    rows: map.rows.map((row) => ({
      ...row,
      seats: row.seats.map((seat) =>
        mine.has(seat.seatId) ? { ...seat, status: "HELD" as const, heldByMe: true } : seat,
      ),
    })),
  });
}

function withCounts(map: SeatMap): SeatMap {
  let available = 0;
  for (const row of map.rows) {
    for (const seat of row.seats) {
      if (seat.status === "AVAILABLE") {
        available++;
      }
    }
  }
  return { ...map, availableSeats: available };
}

export function allSeats(map: SeatMap): (Seat & { row: number })[] {
  return map.rows.flatMap((row) => row.seats.map((seat) => ({ ...seat, row: row.row })));
}

export type Toggle = { selected: number[]; refused?: "limit" | "taken" };

/** Adds a seat to the selection or takes it out. A seat that is not free, or a ninth seat, is refused. */
export function toggleSeat(map: SeatMap, selected: number[], seatId: number): Toggle {
  if (selected.includes(seatId)) {
    return { selected: selected.filter((id) => id !== seatId) };
  }
  const seat = allSeats(map).find((candidate) => candidate.seatId === seatId);
  if (!seat || seat.status !== "AVAILABLE") {
    return { selected, refused: "taken" };
  }
  if (selected.length >= MAX_SEATS) {
    return { selected, refused: "limit" };
  }
  return { selected: [...selected, seatId] };
}

/**
 * Drops from the selection every seat somebody else took in the meantime.
 * Returns what is left and what was lost, so the page can say so.
 */
export function pruneSelection(map: SeatMap, selected: number[]): { selected: number[]; lost: number[] } {
  const free = new Set(
    allSeats(map)
      .filter((seat) => seat.status === "AVAILABLE")
      .map((seat) => seat.seatId),
  );
  const kept = selected.filter((id) => free.has(id));
  return kept.length === selected.length
    ? { selected, lost: [] }
    : { selected: kept, lost: selected.filter((id) => !free.has(id)) };
}

/** The picked seats, in hall order, with their rows: what the order summary lists. */
export function seatsById(map: SeatMap, seatIds: number[]): (Seat & { row: number })[] {
  const wanted = new Set(seatIds);
  return allSeats(map).filter((seat) => wanted.has(seat.seatId));
}

export function totalCents(seats: { price: number }[]): number {
  return sumCents(seats.map((seat) => seat.price));
}

/** "Row 9, seats 4 and 5" style labels, one per row, for a list of tickets. */
export function describeSeats(seats: { row: number; number?: number; seatNumber?: number }[]): string {
  const byRow = new Map<number, number[]>();
  for (const seat of seats) {
    const number = seat.number ?? seat.seatNumber ?? 0;
    byRow.set(seat.row, [...(byRow.get(seat.row) ?? []), number]);
  }
  return [...byRow.entries()]
    .sort(([a], [b]) => a - b)
    .map(([row, numbers]) => {
      const sorted = [...numbers].sort((a, b) => a - b);
      return `Row ${row}, ${sorted.length === 1 ? "seat" : "seats"} ${sorted.join(", ")}`;
    })
    .join(" · ");
}
