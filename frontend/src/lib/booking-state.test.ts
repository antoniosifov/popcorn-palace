import { describe, expect, it } from "vitest";

import { hold, HOLD_ID, seatMap } from "@/test/fixtures";

import { bookingReducer, initialBookingState, type BookingAction, type BookingState } from "./booking-state";
import { allSeats } from "./seatmap";

const run = (state: BookingState, ...actions: BookingAction[]) => actions.reduce(bookingReducer, state);
const seat = (state: BookingState, seatId: number) =>
  allSeats(state.map).find((candidate) => candidate.seatId === seatId);

describe("picking", () => {
  it("collects the seats the customer clicks", () => {
    const state = run(
      initialBookingState(seatMap(), null),
      { type: "toggle", seatId: 1 },
      { type: "toggle", seatId: 5 },
    );

    expect(state.selected).toEqual([1, 5]);
  });

  it("says so when somebody else takes a picked seat, and lets go of it", () => {
    const picking = run(
      initialBookingState(seatMap(), null),
      { type: "toggle", seatId: 1 },
      { type: "toggle", seatId: 5 },
    );

    const state = run(picking, {
      type: "seats",
      event: { showtimeId: 7, seatIds: [5], status: "HELD", holdId: "someone-else" },
    });

    expect(state.selected).toEqual([1]);
    expect(state.notice).toEqual({ tone: "info", text: "Row 2, seat 1 was just taken by somebody else." });
  });

  it("stays quiet about changes to seats the customer did not pick", () => {
    const picking = run(initialBookingState(seatMap(), null), { type: "toggle", seatId: 1 });

    const state = run(picking, {
      type: "seats",
      event: { showtimeId: 7, seatIds: [3], status: "BOOKED", holdId: null },
    });

    expect(state.selected).toEqual([1]);
    expect(state.notice).toBeNull();
    expect(seat(state, 3)?.status).toBe("BOOKED");
  });

  it("explains the limit instead of silently ignoring a ninth seat", () => {
    let state = initialBookingState(seatMap(), null);
    state = { ...state, selected: [11, 12, 13, 14, 15, 16, 17, 18] };

    state = run(state, { type: "toggle", seatId: 1 });

    expect(state.selected).toHaveLength(8);
    expect(state.notice?.text).toBe("You can book up to 8 seats at once.");
  });
});

describe("holding", () => {
  it("turns the picked seats into the customer's hold", () => {
    const picking = run(
      initialBookingState(seatMap(), null),
      { type: "toggle", seatId: 1 },
      { type: "toggle", seatId: 5 },
    );

    const state = run(picking, { type: "held", hold: hold() });

    expect(state.hold?.id).toBe(HOLD_ID);
    expect(state.selected).toEqual([]);
    expect(seat(state, 1)).toMatchObject({ status: "HELD", heldByMe: true });
    expect(state.map.availableSeats).toBe(4);
  });

  it("does not let seats be picked while a hold is being paid for", () => {
    const holding = run(initialBookingState(seatMap(), null), { type: "held", hold: hold() });

    expect(run(holding, { type: "toggle", seatId: 2 })).toBe(holding);
  });

  it("takes the seats named in a refusal out of the selection and keeps the rest", () => {
    const picking = run(
      initialBookingState(seatMap(), null),
      { type: "toggle", seatId: 1 },
      { type: "toggle", seatId: 2 },
    );

    const state = run(picking, {
      type: "failed",
      error: {
        code: "SEAT_ALREADY_HELD",
        message: "Somebody else is booking some of these seats.",
        seatIds: [2],
        traceId: "abc",
      },
    });

    expect(state.selected).toEqual([1]);
    expect(state.notice).toMatchObject({ tone: "error", traceId: "abc", code: "SEAT_ALREADY_HELD" });
  });

  it("recognises the customer's own hold in the stream, whichever arrives first", () => {
    // The event of the stream can overtake the answer to the request that caused it.
    const eventFirst = run(
      initialBookingState(seatMap(), null),
      { type: "seats", event: { showtimeId: 7, seatIds: [1, 5], status: "HELD", holdId: HOLD_ID } },
      { type: "held", hold: hold() },
    );
    const answerFirst = run(
      initialBookingState(seatMap(), null),
      { type: "held", hold: hold() },
      { type: "seats", event: { showtimeId: 7, seatIds: [1, 5], status: "HELD", holdId: HOLD_ID } },
    );

    for (const state of [eventFirst, answerFirst]) {
      expect(seat(state, 1)).toMatchObject({ status: "HELD", heldByMe: true });
      expect(seat(state, 5)).toMatchObject({ status: "HELD", heldByMe: true });
      expect(state.hold?.id).toBe(HOLD_ID);
    }
  });
});

describe("the end of a hold", () => {
  const holding = () => run(initialBookingState(seatMap(), null), { type: "held", hold: hold() });

  it("gives the seats back and keeps them picked when the customer wants to change them", () => {
    const state = run(holding(), { type: "released" });

    expect(state.hold).toBeNull();
    expect(state.selected).toEqual([1, 5]);
    expect(seat(state, 1)).toMatchObject({ status: "AVAILABLE", heldByMe: false });
    expect(state.notice).toBeNull();
  });

  it("is noticed when the server puts the held seats back on sale", () => {
    const state = run(holding(), {
      type: "seats",
      event: { showtimeId: 7, seatIds: [1, 5], status: "AVAILABLE", holdId: null },
    });

    expect(state.hold).toBeNull();
    expect(state.notice?.text).toContain("Your hold ran out");
  });

  it("is not mistaken for running out when the customer released it and the event follows", () => {
    const state = run(
      holding(),
      { type: "released" },
      { type: "seats", event: { showtimeId: 7, seatIds: [1, 5], status: "AVAILABLE", holdId: null } },
    );

    expect(state.notice).toBeNull();
    expect(state.selected).toEqual([1, 5]);
  });

  it("is noticed when paying is refused because the hold is gone", () => {
    const state = run(holding(), {
      type: "failed",
      error: { code: "HOLD_NOT_FOUND", message: "Your hold has run out." },
    });

    expect(state.hold).toBeNull();
    expect(state.notice?.tone).toBe("error");
  });

  it("keeps the hold when the card is declined, so the customer can try another", () => {
    const state = run(holding(), {
      type: "failed",
      error: { code: "PAYMENT_DECLINED", message: "The card was declined." },
    });

    expect(state.hold?.id).toBe(HOLD_ID);
    expect(state.notice?.text).toBe("The card was declined.");
  });
});

describe("loading the map afresh", () => {
  it("replaces what was known and keeps what the customer picked, if it is still free", () => {
    const picking = run(
      initialBookingState(seatMap(), null),
      { type: "toggle", seatId: 1 },
      { type: "toggle", seatId: 2 },
    );

    const state = run(picking, { type: "reloaded", map: seatMap({ 2: { status: "BOOKED" } }), hold: null });

    expect(state.selected).toEqual([1]);
    expect(seat(state, 2)?.status).toBe("BOOKED");
  });

  it("picks up a hold made elsewhere", () => {
    const state = run(initialBookingState(seatMap(), null), {
      type: "reloaded",
      map: seatMap({ 1: { status: "HELD", heldByMe: true }, 5: { status: "HELD", heldByMe: true } }),
      hold: hold(),
    });

    expect(state.hold?.id).toBe(HOLD_ID);
  });

  it("says that the hold ran out when the server no longer has it", () => {
    const holding = run(initialBookingState(seatMap(), null), { type: "held", hold: hold() });

    const state = run(holding, { type: "reloaded", map: seatMap(), hold: null });

    expect(state.hold).toBeNull();
    expect(state.notice?.text).toContain("Your hold ran out");
  });
});
