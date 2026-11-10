import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { Booking, Hold, SeatEvent, SeatMap } from "@/lib/api/types";
import type { ActionResult } from "@/lib/errors";
import { hold, HOLD_ID, seatMap } from "@/test/fixtures";

import { SeatBooking } from "./seat-booking";

// The Server Actions and the router are the edges of this component: they are replaced, everything else is real.
const actions = vi.hoisted(() => ({
  holdSeats: vi.fn<(showtimeId: number, seatIds: number[]) => Promise<ActionResult<Hold>>>(),
  releaseSeats: vi.fn<(showtimeId: number, holdId: string) => Promise<ActionResult<undefined>>>(),
  payForHold:
    vi.fn<(showtimeId: number, holdId: string, paymentToken: string, key: string) => Promise<ActionResult<Booking>>>(),
}));
const router = vi.hoisted(() => ({ push: vi.fn() }));

vi.mock("@/actions/checkout", () => actions);
vi.mock("next/navigation", () => ({ useRouter: () => router }));

/** Stands in for the browser's EventSource: the test decides when the stream opens and what it says. */
class FakeEventSource {
  static last: FakeEventSource | null = null;
  private listeners = new Map<string, ((message: MessageEvent<string>) => void)[]>();
  onerror: (() => void) | null = null;
  closed = false;

  constructor(readonly url: string) {
    FakeEventSource.last = this;
  }
  addEventListener(type: string, listener: (message: MessageEvent<string>) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }
  close() {
    this.closed = true;
  }
  emit(type: string, data: unknown) {
    for (const listener of this.listeners.get(type) ?? []) {
      listener({ data: JSON.stringify(data) } as MessageEvent<string>);
    }
  }
}

/** What the page gets when it asks for the seat map afresh. */
let server: { map: SeatMap; hold: Hold | null };

beforeEach(() => {
  server = { map: seatMap(), hold: null };
  FakeEventSource.last = null;
  vi.stubGlobal("EventSource", FakeEventSource);
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => new Response(JSON.stringify(server), { status: 200 })),
  );
  sessionStorage.clear();
  vi.useFakeTimers({ shouldAdvanceTime: true, toFake: ["Date"] });
  vi.setSystemTime(new Date("2030-01-15T18:00:19Z"));
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

function renderBooking(props: Partial<Parameters<typeof SeatBooking>[0]> = {}) {
  render(
    <SeatBooking
      showtimeId={7}
      initialMap={seatMap()}
      initialHold={null}
      loggedIn
      loginHref="/login?next=%2Fshowtimes%2F7"
      currency="USD"
      started={false}
      {...props}
    />,
  );
  return userEvent.setup();
}

const stream = () => FakeEventSource.last as FakeEventSource;
const seatButton = (row: number, number: number) =>
  screen.getByRole("button", { name: new RegExp(`^Row ${row}, seat ${number},`) });
const order = () => within(screen.getByRole("region", { name: "Your order" }));
/** The line above the hall: "5 of 6 seats free". */
const freeSeats = () => screen.getByText(/seats free/);

async function openStream() {
  await act(async () => stream().emit("connected", { showtimeId: 7 }));
}

async function seats(event: Omit<SeatEvent, "showtimeId">) {
  await act(async () => stream().emit("seats", { showtimeId: 7, ...event }));
}

const booked: Booking = {
  id: "860324e2-4308-4888-9252-9c1b1805972a",
  status: "CONFIRMED",
  showtimeId: 7,
  movieTitle: "Interstellar",
  hallName: "Studio",
  startTime: "2030-01-15T20:00:00Z",
  seats: hold().seats,
  totalPrice: 36.5,
  payment: { id: "p1", status: "SUCCEEDED", amount: 36.5, currency: "USD" },
  userId: "u1",
  createdAt: "2030-01-15T18:01:00Z",
  cancelledAt: null,
};

describe("the live seat map", () => {
  it("subscribes to the showtime's stream and, once it is open, loads the map as it is now", async () => {
    server.map = seatMap({ 3: { status: "BOOKED" } });
    renderBooking();

    expect(stream().url).toBe("/api/showtimes/7/seats/stream");
    expect(screen.getByText("Connecting")).toBeInTheDocument();

    await openStream();

    expect(await screen.findByText("Live")).toBeInTheDocument();
    // Seat 3 was sold between the page being rendered and the stream opening.
    expect(await screen.findByRole("img", { name: /^Row 1, seat 3, .* sold$/ })).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith("/api/showtimes/7/seats", { cache: "no-store" });
  });

  it("shows seats being taken by other customers as it happens", async () => {
    renderBooking();
    await openStream();
    expect(freeSeats()).toHaveTextContent("6 of 6 seats free");

    await seats({ seatIds: [2], status: "HELD", holdId: "someone-else" });

    expect(screen.getByRole("img", { name: /^Row 1, seat 2, .* being booked by someone else$/ })).toBeInTheDocument();
    expect(freeSeats()).toHaveTextContent("5 of 6 seats free");

    await seats({ seatIds: [2], status: "AVAILABLE", holdId: null });

    expect(seatButton(1, 2)).toBeEnabled();
  });

  it("takes a picked seat out of the order when somebody else gets it first, and says so", async () => {
    const user = renderBooking();
    await openStream();
    await user.click(seatButton(1, 1));
    await user.click(seatButton(2, 1));
    expect(order().getByText("$36.50")).toBeInTheDocument();

    await seats({ seatIds: [5], status: "HELD", holdId: "someone-else" });

    expect(order().getByText("Row 2, seat 1 was just taken by somebody else.")).toBeInTheDocument();
    expect(order().getByRole("button", { name: "Hold 1 seat · $14.50" })).toBeInTheDocument();
  });

  it("says when the stream is down and loads the map again when it is back", async () => {
    renderBooking();
    await openStream();

    await act(async () => stream().onerror?.());
    expect(screen.getByText("Reconnecting")).toBeInTheDocument();

    server.map = seatMap({ 4: { status: "BOOKED" } });
    await openStream();

    expect(await screen.findByRole("img", { name: /^Row 1, seat 4, .* sold$/ })).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("closes the stream when the page is left", async () => {
    const { unmount } = render(
      <SeatBooking
        showtimeId={7}
        initialMap={seatMap()}
        initialHold={null}
        loggedIn
        loginHref="/login"
        currency="USD"
        started={false}
      />,
    );
    const opened = stream();

    unmount();

    expect(opened.closed).toBe(true);
  });
});

describe("picking and holding seats", () => {
  it("adds up the order as seats are picked", async () => {
    const user = renderBooking();

    expect(order().getByRole("button", { name: "Hold seats" })).toBeDisabled();

    await user.click(seatButton(1, 1));
    await user.click(seatButton(1, 2));
    await user.click(seatButton(2, 1));

    expect(order().getByText("Row 1, seats 1, 2 · Row 2, seat 1")).toBeInTheDocument();
    expect(order().getByRole("button", { name: "Hold 3 seats · $51.00" })).toBeEnabled();

    await user.click(seatButton(1, 2));

    expect(order().getByRole("button", { name: "Hold 2 seats · $36.50" })).toBeEnabled();
  });

  it("asks a visitor who is not logged in to log in, and brings them back to this showtime", async () => {
    const user = renderBooking({ loggedIn: false });
    await user.click(seatButton(1, 1));

    expect(order().getByRole("link", { name: "Log in to book" })).toHaveAttribute(
      "href",
      "/login?next=%2Fshowtimes%2F7",
    );
    expect(order().queryByRole("button", { name: /Hold/ })).not.toBeInTheDocument();
  });

  it("holds the picked seats and starts the countdown to pay", async () => {
    actions.holdSeats.mockResolvedValue({ ok: true, data: hold() });
    const user = renderBooking();
    await user.click(seatButton(1, 1));
    await user.click(seatButton(2, 1));

    await user.click(order().getByRole("button", { name: "Hold 2 seats · $36.50" }));

    expect(actions.holdSeats).toHaveBeenCalledWith(7, [1, 5]);
    expect(await order().findByRole("heading", { name: "Your seats are held" })).toBeInTheDocument();
    // The hold expires at 18:10:00 and it is 18:00:19.
    expect(order().getByRole("timer")).toHaveTextContent("9:41");
    expect(order().getByRole("button", { name: "Pay $36.50" })).toBeEnabled();
    // The held seats are the customer's and no longer something to click.
    expect(screen.getByRole("img", { name: /^Row 1, seat 1, .* yours$/ })).toBeInTheDocument();
  });

  it("when a seat was lost in the race, keeps the others picked and shows the reference of the request", async () => {
    actions.holdSeats.mockResolvedValue({
      ok: false,
      error: {
        code: "SEAT_ALREADY_HELD",
        message: "Somebody else is booking some of these seats right now. Pick other seats.",
        seatIds: [5],
        traceId: "4bf92f3577b34da6",
      },
    });
    const user = renderBooking();
    await user.click(seatButton(1, 1));
    await user.click(seatButton(2, 1));

    await user.click(order().getByRole("button", { name: /Hold 2 seats/ }));

    expect(await order().findByRole("alert")).toHaveTextContent(
      "Somebody else is booking some of these seats right now.",
    );
    expect(order().getByText("Reference 4bf92f3577b34da6")).toBeInTheDocument();
    expect(order().getByRole("button", { name: "Hold 1 seat · $14.50" })).toBeInTheDocument();
  });

  it("gives the seats back when the customer wants to change them, without waiting for the server", async () => {
    let finish: (result: ActionResult<undefined>) => void = () => {};
    actions.releaseSeats.mockReturnValue(new Promise((resolve) => (finish = resolve)));
    const user = renderBooking({
      initialHold: hold(),
      initialMap: seatMap({ 1: { status: "HELD", heldByMe: true }, 5: { status: "HELD", heldByMe: true } }),
    });

    await user.click(order().getByRole("button", { name: "Change seats" }));

    // Back to picking at once, with the same seats still picked.
    expect(order().getByRole("heading", { name: "Your seats" })).toBeInTheDocument();
    expect(order().getByText("$36.50")).toBeInTheDocument();
    expect(actions.releaseSeats).toHaveBeenCalledWith(7, HOLD_ID);
    await act(async () => finish({ ok: true, data: undefined }));
  });

  it("asks the server what happened when the countdown reaches zero", async () => {
    renderBooking({
      initialHold: hold(),
      initialMap: seatMap({ 1: { status: "HELD", heldByMe: true }, 5: { status: "HELD", heldByMe: true } }),
    });
    expect(order().getByRole("timer")).toHaveTextContent("9:41");

    // The server has let go of the hold; the clock of this device reaches 18:10:00.
    server = { map: seatMap(), hold: null };
    await act(async () => {
      vi.setSystemTime(new Date("2030-01-15T18:10:00Z"));
      await new Promise((resolve) => setTimeout(resolve, 1100));
    });

    expect(await order().findByText(/Your hold ran out/)).toBeInTheDocument();
    expect(order().getByRole("heading", { name: "Your seats" })).toBeInTheDocument();
  });
});

describe("paying", () => {
  const holding = () =>
    renderBooking({
      initialHold: hold(),
      initialMap: seatMap({ 1: { status: "HELD", heldByMe: true }, 5: { status: "HELD", heldByMe: true } }),
    });
  const keyOf = (call: number) => actions.payForHold.mock.calls[call][3];

  it("pays with the chosen test card and opens the booking", async () => {
    actions.payForHold.mockResolvedValue({ ok: true, data: booked });
    const user = holding();

    await user.click(order().getByRole("button", { name: "Pay $36.50" }));

    expect(actions.payForHold).toHaveBeenCalledWith(7, HOLD_ID, "tok_visa", expect.stringMatching(/^[0-9a-f-]{36}$/));
    await waitFor(() => expect(router.push).toHaveBeenCalledWith(`/bookings/${booked.id}`));
  });

  it("after a declined card keeps the hold and makes the next press a new attempt, with a new key", async () => {
    actions.payForHold
      .mockResolvedValueOnce({
        ok: false,
        error: {
          code: "PAYMENT_DECLINED",
          message: "The card was declined: insufficient funds. Your seats are still held. Try another card.",
        },
      })
      .mockResolvedValueOnce({ ok: true, data: booked });
    const user = holding();

    await user.click(order().getByRole("radio", { name: /Test card 3/ }));
    await user.click(order().getByRole("button", { name: "Pay $36.50" }));

    expect(await order().findByRole("alert")).toHaveTextContent("The card was declined: insufficient funds.");
    expect(order().getByRole("heading", { name: "Your seats are held" })).toBeInTheDocument();

    await user.click(order().getByRole("radio", { name: /Test card 1/ }));
    await user.click(order().getByRole("button", { name: "Pay $36.50" }));

    await waitFor(() => expect(router.push).toHaveBeenCalled());
    expect(actions.payForHold.mock.calls.map((call) => call[2])).toEqual(["tok_declined", "tok_visa"]);
    // The API replays the recorded decline for a used key, so the second attempt must not reuse it.
    expect(keyOf(1)).not.toBe(keyOf(0));
  });

  it("when the answer never arrives, repeats the same attempt with the same key, so nobody is charged twice", async () => {
    actions.payForHold
      .mockRejectedValueOnce(new TypeError("Failed to fetch"))
      .mockResolvedValueOnce({ ok: true, data: booked });
    const user = holding();

    await user.click(order().getByRole("button", { name: "Pay $36.50" }));

    expect(await order().findByRole("alert")).toHaveTextContent("You will not be charged twice.");

    await user.click(order().getByRole("button", { name: "Pay $36.50" }));

    await waitFor(() => expect(router.push).toHaveBeenCalled());
    expect(keyOf(1)).toBe(keyOf(0));
  });

  it("keeps the key of an unfinished attempt across a reload of the page", async () => {
    actions.payForHold.mockRejectedValue(new TypeError("Failed to fetch"));
    const user = holding();
    await user.click(order().getByRole("button", { name: "Pay $36.50" }));
    await order().findByRole("alert");
    const firstKey = keyOf(0);

    // The customer reloads: a new page, the same hold.
    document.body.innerHTML = "";
    const again = holding();
    await again.click(order().getByRole("button", { name: "Pay $36.50" }));

    await waitFor(() => expect(actions.payForHold).toHaveBeenCalledTimes(2));
    expect(keyOf(1)).toBe(firstKey);
  });

  it("goes back to picking when the hold ran out while paying", async () => {
    actions.payForHold.mockResolvedValue({
      ok: false,
      error: {
        code: "HOLD_NOT_FOUND",
        message: "Your hold has run out and the seats are back on sale. Pick your seats again.",
      },
    });
    const user = holding();

    await user.click(order().getByRole("button", { name: "Pay $36.50" }));

    expect(await order().findByRole("alert")).toHaveTextContent("Your hold has run out");
    expect(order().getByRole("heading", { name: "Your seats" })).toBeInTheDocument();
  });
});

describe("a showtime that has started", () => {
  it("shows the hall but sells nothing", () => {
    renderBooking({ started: true });

    expect(order().getByRole("heading", { name: "Booking is closed" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Row 1, seat 1/ })).not.toBeInTheDocument();
  });
});
