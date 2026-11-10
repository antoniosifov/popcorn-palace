import type { Hold, Seat, SeatMap } from "@/lib/api/types";

/**
 * A small hall for tests: row 1 has four standard seats (ids 1-4) at 14.50,
 * row 2 has two VIP seats (ids 5-6) at 22.00.
 */
export function seatMap(overrides: Record<number, Partial<Seat>> = {}): SeatMap {
  const seat = (seatId: number, number: number, vip: boolean): Seat => ({
    seatId,
    number,
    category: vip ? "VIP" : "STANDARD",
    price: vip ? 22 : 14.5,
    status: "AVAILABLE",
    heldByMe: false,
    ...overrides[seatId],
  });
  const rows = [
    { row: 1, seats: [seat(1, 1, false), seat(2, 2, false), seat(3, 3, false), seat(4, 4, false)] },
    { row: 2, seats: [seat(5, 1, true), seat(6, 2, true)] },
  ];
  const available = rows.flatMap((row) => row.seats).filter((candidate) => candidate.status === "AVAILABLE").length;
  return { showtimeId: 7, hallId: 1, hallName: "Studio", totalSeats: 6, availableSeats: available, rows };
}

export const HOLD_ID = "711c015e-6ea7-4222-bb31-87e2188ca53e";

/** A hold of seats 1 and 5: one standard, one VIP, 36.50 together. */
export function hold(overrides: Partial<Hold> = {}): Hold {
  return {
    id: HOLD_ID,
    showtimeId: 7,
    seats: [
      { seatId: 1, row: 1, seatNumber: 1, category: "STANDARD", price: 14.5 },
      { seatId: 5, row: 2, seatNumber: 1, category: "VIP", price: 22 },
    ],
    totalPrice: 36.5,
    expiresAt: "2030-01-15T18:10:00Z",
    ...overrides,
  };
}
