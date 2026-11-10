import { describe, expect, it } from "vitest";

import { seatMap } from "@/test/fixtures";

import { formatCents, formatMoney, sumCents } from "./money";
import {
  allSeats,
  applySeatEvent,
  describeSeats,
  markHeldByMe,
  MAX_SEATS,
  pruneSelection,
  seatsById,
  toggleSeat,
  totalCents,
} from "./seatmap";

const statusOf = (map: ReturnType<typeof seatMap>, seatId: number) =>
  allSeats(map).find((seat) => seat.seatId === seatId);

describe("events of the live stream", () => {
  it("mark seats as held and count them out of the free ones", () => {
    const map = applySeatEvent(seatMap(), { showtimeId: 7, seatIds: [1, 2], status: "HELD", holdId: "someone" }, null);

    expect(statusOf(map, 1)).toMatchObject({ status: "HELD", heldByMe: false });
    expect(statusOf(map, 3)?.status).toBe("AVAILABLE");
    expect(map.availableSeats).toBe(4);
  });

  it("tell the customer's own hold from everybody else's by its id", () => {
    const map = applySeatEvent(seatMap(), { showtimeId: 7, seatIds: [1], status: "HELD", holdId: "mine" }, "mine");

    expect(statusOf(map, 1)).toMatchObject({ status: "HELD", heldByMe: true });
  });

  it("put released seats back on sale, and they are nobody's", () => {
    const held = seatMap({ 1: { status: "HELD", heldByMe: true } });

    const map = applySeatEvent(held, { showtimeId: 7, seatIds: [1], status: "AVAILABLE", holdId: null }, "mine");

    expect(statusOf(map, 1)).toMatchObject({ status: "AVAILABLE", heldByMe: false });
    expect(map.availableSeats).toBe(6);
  });

  it("mark sold seats as sold", () => {
    const map = applySeatEvent(seatMap(), { showtimeId: 7, seatIds: [5], status: "BOOKED", holdId: null }, null);

    expect(statusOf(map, 5)?.status).toBe("BOOKED");
  });

  it("change nothing when they are about another showtime or say what is already known", () => {
    const map = seatMap({ 1: { status: "BOOKED" } });

    expect(applySeatEvent(map, { showtimeId: 8, seatIds: [2], status: "HELD", holdId: "x" }, null)).toBe(map);
    expect(applySeatEvent(map, { showtimeId: 7, seatIds: [1], status: "BOOKED", holdId: null }, null)).toBe(map);
  });

  it("leave the map they were applied to untouched", () => {
    const before = seatMap();

    applySeatEvent(before, { showtimeId: 7, seatIds: [1], status: "BOOKED", holdId: null }, null);

    expect(statusOf(before, 1)?.status).toBe("AVAILABLE");
  });
});

describe("picking seats", () => {
  it("adds a free seat and takes it out again", () => {
    const map = seatMap();

    const picked = toggleSeat(map, [], 2);
    expect(picked).toEqual({ selected: [2] });
    expect(toggleSeat(map, picked.selected, 2)).toEqual({ selected: [] });
  });

  it("refuses a seat that is held or sold", () => {
    const map = seatMap({ 2: { status: "HELD" }, 3: { status: "BOOKED" } });

    expect(toggleSeat(map, [], 2)).toEqual({ selected: [], refused: "taken" });
    expect(toggleSeat(map, [], 3)).toEqual({ selected: [], refused: "taken" });
    expect(toggleSeat(map, [], 999)).toEqual({ selected: [], refused: "taken" });
  });

  it("refuses a ninth seat", () => {
    const eight = Array.from({ length: MAX_SEATS }, (_, index) => 100 + index);

    expect(toggleSeat(seatMap(), eight, 1)).toEqual({ selected: eight, refused: "limit" });
  });

  it("drops picked seats that somebody else took, and says which", () => {
    const map = seatMap({ 2: { status: "HELD" } });

    expect(pruneSelection(map, [1, 2, 5])).toEqual({ selected: [1, 5], lost: [2] });
  });

  it("keeps the very same selection when nothing was lost", () => {
    const selected = [1, 5];

    expect(pruneSelection(seatMap(), selected).selected).toBe(selected);
  });

  it("marks seats as the customer's own once they are held", () => {
    const map = markHeldByMe(seatMap(), [1, 5]);

    expect(statusOf(map, 1)).toMatchObject({ status: "HELD", heldByMe: true });
    expect(map.availableSeats).toBe(4);
  });
});

describe("the order", () => {
  it("lists picked seats in hall order with their rows", () => {
    expect(seatsById(seatMap(), [5, 1]).map((seat) => [seat.row, seat.number])).toEqual([
      [1, 1],
      [2, 1],
    ]);
  });

  it("adds prices up in whole cents", () => {
    // 14.50 + 14.50 + 22.00, and the classic 0.1 + 0.2.
    expect(totalCents(seatsById(seatMap(), [1, 2, 5]))).toBe(5100);
    expect(sumCents([0.1, 0.2])).toBe(30);
  });

  it("writes money with two decimals and separators", () => {
    expect(formatMoney(22, "USD")).toBe("$22.00");
    expect(formatMoney(14.5, "USD")).toBe("$14.50");
    expect(formatCents(123456, "USD")).toBe("$1,234.56");
    expect(formatMoney(22, "EUR")).toBe("€22.00");
  });

  it("describes seats row by row", () => {
    expect(
      describeSeats([
        { row: 9, number: 5 },
        { row: 9, number: 4 },
      ]),
    ).toBe("Row 9, seats 4, 5");
    expect(describeSeats([{ row: 2, seatNumber: 1 }])).toBe("Row 2, seat 1");
    expect(
      describeSeats([
        { row: 3, number: 1 },
        { row: 1, number: 7 },
      ]),
    ).toBe("Row 1, seat 7 · Row 3, seat 1");
  });
});
