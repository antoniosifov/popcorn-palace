import type { Hold, SeatEvent, SeatMap } from "./api/types";
import type { ActionError } from "./errors";
import { applySeatEvent, describeSeats, markHeldByMe, pruneSelection, seatsById, toggleSeat } from "./seatmap";

/**
 * Everything the seat picker knows, and every way it can change: the seat map
 * as last heard from the server, the seats the customer has picked, the hold
 * they have, and one message. A reducer, so each step from picking seats to
 * paying is a plain function of the state before it.
 */

export type Notice = { tone: "info" | "error"; text: string; traceId?: string; code?: string };

export type BookingState = {
  map: SeatMap;
  /** Seats picked but not yet held, in the order they were picked. */
  selected: number[];
  /** The customer's hold: seats reserved for them while they pay. */
  hold: Hold | null;
  notice: Notice | null;
};

export type BookingAction =
  /** A seat changed status somewhere: an event of the live stream. */
  | { type: "seats"; event: SeatEvent }
  /** The customer clicked a seat. */
  | { type: "toggle"; seatId: number }
  /** The picked seats are now held for the customer. */
  | { type: "held"; hold: Hold }
  /** Holding, releasing or paying was refused. */
  | { type: "failed"; error: ActionError }
  /** The customer gave the seats back to pick again. */
  | { type: "released" }
  /** The time to pay ran out. */
  | { type: "expired" }
  /** The seat map was loaded afresh (the live stream had been away). */
  | { type: "reloaded"; map: SeatMap; hold: Hold | null }
  | { type: "notice"; notice: Notice | null };

export function initialBookingState(map: SeatMap, hold: Hold | null): BookingState {
  return { map, selected: [], hold, notice: null };
}

const EXPIRED: Notice = {
  tone: "info",
  text: "Your hold ran out and the seats went back on sale. Pick your seats again.",
};

export function bookingReducer(state: BookingState, action: BookingAction): BookingState {
  switch (action.type) {
    case "seats": {
      const map = applySeatEvent(state.map, action.event, state.hold?.id ?? null);
      if (map === state.map) {
        return state;
      }
      // The customer's held seats came back on sale: the hold is gone (it ran out).
      if (state.hold && action.event.status === "AVAILABLE") {
        const freed = new Set(action.event.seatIds);
        if (state.hold.seats.some((seat) => freed.has(seat.seatId))) {
          return { ...state, map, hold: null, notice: EXPIRED };
        }
      }
      // Somebody took a seat the customer had picked but not held yet.
      const { selected, lost } = pruneSelection(map, state.selected);
      if (lost.length === 0) {
        return { ...state, map };
      }
      return { ...state, map, selected, notice: { tone: "info", text: lostText(state.map, lost) } };
    }

    case "toggle": {
      if (state.hold) {
        return state;
      }
      const { selected, refused } = toggleSeat(state.map, state.selected, action.seatId);
      if (refused === "limit") {
        return { ...state, notice: { tone: "info", text: "You can book up to 8 seats at once." } };
      }
      return refused ? state : { ...state, selected, notice: null };
    }

    case "held":
      return {
        map: markHeldByMe(
          state.map,
          action.hold.seats.map((seat) => seat.seatId),
        ),
        selected: [],
        hold: action.hold,
        notice: null,
      };

    case "failed": {
      const notice: Notice = {
        tone: "error",
        text: action.error.message,
        traceId: action.error.traceId,
        code: action.error.code,
      };
      if (action.error.code === "HOLD_NOT_FOUND") {
        // The hold ran out while the customer was paying.
        return { ...state, hold: null, notice };
      }
      // Seats named in the refusal belong to somebody else now: take them out of the selection.
      const lost = new Set(action.error.seatIds ?? []);
      return { ...state, selected: state.selected.filter((id) => !lost.has(id)), notice };
    }

    case "released": {
      if (!state.hold) {
        return state;
      }
      // The seats are free again and stay picked, so the customer can adjust the choice.
      const seatIds = state.hold.seats.map((seat) => seat.seatId);
      const map = applySeatEvent(
        state.map,
        { showtimeId: state.map.showtimeId, seatIds, status: "AVAILABLE", holdId: null },
        null,
      );
      return { map, selected: seatIds, hold: null, notice: null };
    }

    case "expired":
      return state.hold ? { ...state, hold: null, notice: EXPIRED } : state;

    case "reloaded": {
      const { selected } = pruneSelection(action.map, state.selected);
      const lostHold = state.hold !== null && action.hold === null;
      return {
        map: action.map,
        selected: action.hold ? [] : selected,
        hold: action.hold,
        notice: lostHold ? EXPIRED : state.notice,
      };
    }

    case "notice":
      return { ...state, notice: action.notice };
  }
}

function lostText(map: SeatMap, lost: number[]): string {
  const seats = seatsById(map, lost);
  const what = describeSeats(seats);
  return seats.length === 1 ? `${what} was just taken by somebody else.` : `${what} were just taken by somebody else.`;
}
