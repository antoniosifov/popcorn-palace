import { describe, expect, it } from "vitest";

import type { Booking } from "./api/types";
import { bookingGroup, bookingReference, bookingStatus, canCancel } from "./booking-status";
import { monogram } from "@/components/poster";

import { describeProblem } from "./errors";
import { loginPath, safeNextPath } from "./paths";
import { PAYMENT_METHODS, settlesAttempt } from "./payment";

describe("what the customer is told when the API refuses", () => {
  it("uses this application's words for the codes it knows", () => {
    expect(
      describeProblem({ code: "INVALID_CREDENTIALS", detail: "Invalid email or password", status: 401 }).message,
    ).toBe("The email or the password is not right.");
  });

  it("falls back to the API's own sentence for a code it has no words for", () => {
    expect(
      describeProblem({ code: "MOVIE_TITLE_TAKEN", detail: "A movie titled 'Arrival' already exists", status: 409 })
        .message,
    ).toBe("A movie titled 'Arrival' already exists");
  });

  it("does not show the details of a server error", () => {
    const error = describeProblem({ code: "SOMETHING_NEW", detail: "NullPointerException at ...", status: 500 });

    expect(error.message).toBe("Something went wrong on our side. Try again in a moment.");
  });

  it("explains a declined card by its reason and says that the seats are still held", () => {
    expect(describeProblem({ code: "PAYMENT_DECLINED", declineCode: "insufficient_funds", status: 402 }).message).toBe(
      "The card was declined: insufficient funds. Your seats are still held. Try another card.",
    );
    expect(describeProblem({ code: "PAYMENT_DECLINED", declineCode: "something_else", status: 402 }).message).toBe(
      "The card was declined. Your seats are still held. Try another card.",
    );
  });

  it("keeps the lost seats, the existing hold and the trace of the request", () => {
    const error = describeProblem({
      code: "SEAT_ALREADY_HELD",
      status: 409,
      seatIds: [101, 102],
      holdId: "h1",
      traceId: "4bf92f3577b34da6a3ce929d0e0e4736",
    });

    expect(error).toMatchObject({ seatIds: [101, 102], holdId: "h1", traceId: "4bf92f3577b34da6a3ce929d0e0e4736" });
  });

  it("puts each rejected field's message under the field's name", () => {
    const error = describeProblem({
      code: "VALIDATION_FAILED",
      status: 400,
      errors: [
        { field: "durationMinutes", message: "must be greater than 0" },
        { field: "title", message: "must not be blank" },
        { field: "title", message: "size must be between 0 and 200" },
        { field: "prices[VIP]", message: "must not be null" },
      ],
    });

    expect(error.fieldErrors).toEqual({
      durationMinutes: "Must be greater than 0",
      title: "Must not be blank",
      "prices[VIP]": "Must not be null",
    });
  });
});

describe("a purchase attempt and its idempotency key", () => {
  it("is over once the card was declined or the provider failed: the next press is a new attempt", () => {
    for (const code of [
      "PAYMENT_DECLINED",
      "PAYMENT_PROVIDER_UNAVAILABLE",
      "PAYMENT_REFUNDED",
      "HOLD_NOT_FOUND",
      "IDEMPOTENCY_KEY_REUSED",
    ]) {
      expect(settlesAttempt(code), code).toBe(true);
    }
  });

  it("is still open while the outcome is unknown: the same key must be sent again", () => {
    for (const code of ["PAYMENT_IN_PROGRESS", "API_UNREACHABLE", "SERVICE_UNAVAILABLE"]) {
      expect(settlesAttempt(code), code).toBe(false);
    }
  });

  it("offers one test card for every way a payment can go", () => {
    expect(PAYMENT_METHODS.map((method) => method.token)).toEqual([
      "tok_visa",
      "tok_slow",
      "tok_declined",
      "tok_expired",
      "tok_error",
    ]);
  });
});

describe("where to go after logging in", () => {
  it("is the page the visitor came from", () => {
    expect(loginPath("/showtimes/7")).toBe("/login?next=%2Fshowtimes%2F7");
    expect(safeNextPath("/showtimes/7")).toBe("/showtimes/7");
    expect(safeNextPath("/bookings?show=past")).toBe("/bookings?show=past");
  });

  it("is never another site", () => {
    for (const next of [
      "https://evil.example",
      "//evil.example",
      "/\\evil.example",
      "javascript:alert(1)",
      "",
      undefined,
      null,
    ]) {
      expect(safeNextPath(next), String(next)).toBe("/");
    }
  });
});

describe("where a booking stands", () => {
  const now = new Date("2030-01-15T12:00:00Z");
  const booking = (overrides: Partial<Booking>): Booking => ({
    id: "860324e2-4308-4888-9252-9c1b1805972a",
    status: "CONFIRMED",
    showtimeId: 1,
    movieTitle: "Interstellar",
    hallName: "Hall 1",
    startTime: "2030-01-15T18:00:00Z",
    seats: [],
    totalPrice: 44,
    payment: { id: "p1", status: "SUCCEEDED", amount: 44, currency: "USD" },
    userId: "u1",
    createdAt: "2030-01-10T12:03:00Z",
    cancelledAt: null,
    ...overrides,
  });

  it("is upcoming until the showtime starts, and can be cancelled until then", () => {
    const upcoming = booking({});
    expect(bookingGroup(upcoming, now)).toBe("upcoming");
    expect(bookingStatus(upcoming, now)).toEqual({ text: "Confirmed", tone: "positive" });
    expect(canCancel(upcoming, now)).toBe(true);

    const seen = booking({ startTime: "2030-01-15T11:00:00Z" });
    expect(bookingGroup(seen, now)).toBe("past");
    expect(bookingStatus(seen, now).text).toBe("Seen");
    expect(canCancel(seen, now)).toBe(false);
  });

  it("says whether the refund of a cancelled booking has arrived", () => {
    const pending = booking({
      status: "CANCELLED",
      payment: { id: "p1", status: "REFUND_PENDING", amount: 44, currency: "USD" },
    });
    const refunded = booking({
      status: "CANCELLED",
      payment: { id: "p1", status: "REFUNDED", amount: 44, currency: "USD" },
    });

    expect(bookingGroup(pending, now)).toBe("cancelled");
    expect(bookingStatus(pending, now)).toEqual({ text: "Cancelled, refund on its way", tone: "warning" });
    expect(bookingStatus(refunded, now)).toEqual({ text: "Cancelled and refunded", tone: "neutral" });
    expect(canCancel(pending, now)).toBe(false);
  });

  it("has a short reference to read out", () => {
    expect(bookingReference(booking({}))).toBe("860324E2");
  });
});

describe("the title card of a movie", () => {
  it("is a monogram of the first two letters, past a leading article", () => {
    expect(monogram("Interstellar")).toBe("In");
    expect(monogram("The Grand Budapest Hotel")).toBe("Gr");
    expect(monogram("Dune: Part Two")).toBe("Du");
    expect(monogram("2001: A Space Odyssey")).toBe("20");
    expect(monogram("M")).toBe("M");
    expect(monogram("…")).toBe("P");
  });
});
