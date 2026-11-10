"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useReducer, useRef, useState, useTransition } from "react";

import { holdSeats, payForHold, releaseSeats } from "@/actions/checkout";
import type { Hold, SeatEvent, SeatMap } from "@/lib/api/types";
import { bookingReducer, initialBookingState } from "@/lib/booking-state";
import { formatCents, formatMoney } from "@/lib/money";
import { newIdempotencyKey, PAYMENT_METHODS, settlesAttempt } from "@/lib/payment";
import { describeSeats, MAX_SEATS, seatsById, totalCents } from "@/lib/seatmap";
import { formatCountdown } from "@/lib/time";
import { useNowSeconds } from "@/lib/use-now";

import { SeatGrid, SeatLegend } from "./seat-grid";
import { Notice } from "./ui";

type Props = {
  showtimeId: number;
  initialMap: SeatMap;
  initialHold: Hold | null;
  loggedIn: boolean;
  /** Where to log in so that the visitor comes back to this showtime. */
  loginHref: string;
  currency: string;
  /** Tickets can no longer be bought: the showtime has started. */
  started: boolean;
};

type Stream = "connecting" | "live" | "reconnecting";

/** Where the idempotency key of an unfinished payment is kept, so that a reload of the page repeats the same attempt. */
const keyStore = (holdId: string) => `pp:payment-attempt:${holdId}`;

function storedKey(holdId: string): string | null {
  try {
    return sessionStorage.getItem(keyStore(holdId));
  } catch {
    return null;
  }
}

function rememberKey(holdId: string, key: string | null): void {
  try {
    if (key) {
      sessionStorage.setItem(keyStore(holdId), key);
    } else {
      sessionStorage.removeItem(keyStore(holdId));
    }
  } catch {
    // Storage is switched off: the key still lives for as long as the page does.
  }
}

/**
 * The seat map of a showtime and the whole way through it: pick seats, hold
 * them, pay. The map is live: it follows the showtime's event stream, so seats
 * other customers take change in front of the visitor.
 */
export function SeatBooking({ showtimeId, initialMap, initialHold, loggedIn, loginHref, currency, started }: Props) {
  const router = useRouter();
  const [state, dispatch] = useReducer(bookingReducer, initialBookingState(initialMap, initialHold));
  const [stream, setStream] = useState<Stream>("connecting");
  const [method, setMethod] = useState(PAYMENT_METHODS[0].token);
  const [busy, startTransition] = useTransition();
  /** Counts what the customer did, so an answer that set out before it is not applied after it. */
  const changes = useRef(0);
  const attemptKey = useRef<string | null>(null);

  const { map, selected, hold, notice } = state;

  // ------------------------------------------------------------ the live map

  /** Asks for the seat map as it is now. Used whenever the stream (re)connects and when a hold runs out. */
  const reload = useCallback(async () => {
    const before = changes.current;
    try {
      const response = await fetch(`/api/showtimes/${showtimeId}/seats`, { cache: "no-store" });
      if (!response.ok) {
        return;
      }
      const fresh = (await response.json()) as { map: SeatMap; hold: Hold | null };
      if (before === changes.current) {
        dispatch({ type: "reloaded", map: fresh.map, hold: fresh.hold });
      }
    } catch {
      // Offline for a moment: the stream reconnects by itself and asks again.
    }
  }, [showtimeId]);

  useEffect(() => {
    const source = new EventSource(`/api/showtimes/${showtimeId}/seats/stream`);
    // Events are not replayed. Whatever happened before this subscription began
    // (between the page being rendered and now, or while the connection was
    // down) is picked up by loading the map once the stream is open.
    source.addEventListener("connected", () => {
      setStream("live");
      void reload();
    });
    source.addEventListener("seats", (message) => {
      try {
        dispatch({ type: "seats", event: JSON.parse((message as MessageEvent<string>).data) as SeatEvent });
      } catch {
        // Not an event this page understands: ignore it.
      }
    });
    source.onerror = () => setStream("reconnecting");
    return () => source.close();
  }, [showtimeId, reload]);

  // --------------------------------------------------------------- the hold

  const now = useNowSeconds();
  const secondsLeft = hold && now !== null ? Math.ceil(new Date(hold.expiresAt).getTime() / 1000) - now : null;
  const ranOut = secondsLeft !== null && secondsLeft <= 0;
  useEffect(() => {
    if (ranOut) {
      // The clock of this device says the time is up. The server decides: ask it.
      void reload();
    }
  }, [ranOut, reload]);

  function holdPicked() {
    const seatIds = selected;
    startTransition(async () => {
      changes.current++;
      try {
        const result = await holdSeats(showtimeId, seatIds);
        changes.current++;
        if (result.ok) {
          dispatch({ type: "held", hold: result.data });
        } else {
          dispatch({ type: "failed", error: result.error });
          if (result.error.code === "HOLD_ALREADY_EXISTS") {
            // Held in another tab: show that hold.
            await reload();
          }
        }
      } catch {
        dispatch({ type: "notice", notice: OFFLINE });
      }
    });
  }

  function release() {
    if (!hold) {
      return;
    }
    const holdId = hold.id;
    changes.current++;
    // The seats are the customer's to give back, so the page does not wait for the answer.
    dispatch({ type: "released" });
    rememberKey(holdId, null);
    attemptKey.current = null;
    startTransition(async () => {
      try {
        const result = await releaseSeats(showtimeId, holdId);
        if (!result.ok && result.error.code !== "HOLD_NOT_FOUND") {
          dispatch({ type: "failed", error: result.error });
          await reload();
        }
      } catch {
        await reload();
      }
    });
  }

  function pay() {
    if (!hold) {
      return;
    }
    const holdId = hold.id;
    // One key per attempt. The same key goes out again for as long as the
    // outcome is unknown, so pressing Pay twice can never charge twice.
    const key = (attemptKey.current ??= storedKey(holdId) ?? newIdempotencyKey());
    rememberKey(holdId, key);
    startTransition(async () => {
      try {
        const result = await payForHold(showtimeId, holdId, method, key);
        if (result.ok) {
          rememberKey(holdId, null);
          router.push(`/bookings/${result.data.id}`);
          return;
        }
        if (settlesAttempt(result.error.code)) {
          // Declined or failed for good: the next press is a new attempt.
          attemptKey.current = null;
          rememberKey(holdId, null);
        }
        changes.current++;
        dispatch({ type: "failed", error: result.error });
      } catch {
        dispatch({ type: "notice", notice: PAYMENT_UNKNOWN });
      }
    });
  }

  // ------------------------------------------------------------- the screen

  const picked = seatsById(map, selected);
  const pickedTotal = totalCents(picked);
  const expired = notice?.code === "UNAUTHENTICATED";

  const canHold = loggedIn && !expired;
  /** On a phone the order sits below the hall; once seats are picked its action follows the screen. */
  const showBar = !started && !hold && picked.length > 0;

  return (
    <div
      className={`grid items-start gap-3.5 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] ${showBar ? "pb-20 lg:pb-0" : ""}`}
    >
      {showBar ? (
        <div className="fixed inset-x-0 bottom-0 z-10 border-t border-rule bg-surface lg:hidden">
          <div className="column flex items-center justify-between gap-4 py-3">
            <p>
              <span className="block text-label text-muted">
                {picked.length === 1 ? "1 seat" : `${picked.length} seats`}
              </span>
              <span className="num text-section font-semibold text-accent">{formatCents(pickedTotal, currency)}</span>
            </p>
            {canHold ? (
              <button type="button" className="btn btn-primary btn-large" onClick={holdPicked} disabled={busy}>
                {busy ? "Holding…" : "Hold seats"}
              </button>
            ) : (
              <Link href={loginHref} className="btn btn-primary btn-large">
                Log in to book
              </Link>
            )}
          </div>
        </div>
      ) : null}
      <section aria-label="Seat map" className="card p-3.5 sm:p-[22px]">
        <div className="mb-4 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
          <h2 className="text-section font-semibold">{map.hallName}</h2>
          <p className="text-label text-muted" aria-live="polite">
            <span className="num text-ink">{map.availableSeats}</span> of <span className="num">{map.totalSeats}</span>{" "}
            seats free
            <span className="ml-3 text-faint">
              {stream === "live" ? "Live" : stream === "connecting" ? "Connecting" : "Reconnecting"}
            </span>
          </p>
        </div>
        <SeatGrid
          rows={map.rows}
          selected={selected}
          currency={currency}
          locked={busy}
          onToggle={started || hold ? undefined : (seatId) => dispatch({ type: "toggle", seatId })}
        />
        <div className="mt-6">
          <SeatLegend rows={map.rows} currency={currency} />
        </div>
      </section>

      <section aria-label="Your order" className="card lg:sticky lg:top-4">
        {started ? (
          <>
            <h2 className="text-section font-semibold">Booking is closed</h2>
            <p className="mt-2 text-muted">This showtime has already started.</p>
          </>
        ) : hold ? (
          <>
            <div className="flex items-baseline justify-between gap-4">
              <h2 className="text-section font-semibold">Your seats are held</h2>
              <p className="text-label text-muted">
                for{" "}
                <span className="num text-figure font-medium text-ink" role="timer" aria-label="Time left to pay">
                  {secondsLeft === null ? "–:––" : formatCountdown(secondsLeft)}
                </span>
              </p>
            </div>
            <OrderLines
              seats={hold.seats.map((seat) => ({ ...seat, number: seat.seatNumber }))}
              totalCents={Math.round(hold.totalPrice * 100)}
              currency={currency}
            />

            <fieldset className="mt-5" disabled={busy}>
              <legend className="font-medium">Pay with</legend>
              <p className="mt-0.5 text-label text-muted">A sandbox: each test card shows one way a payment can go.</p>
              <div className="mt-2.5 grid gap-1.5">
                {PAYMENT_METHODS.map((option) => (
                  <label
                    key={option.token}
                    className="flex cursor-pointer items-center gap-3 rounded-inner bg-well px-3 py-2.5 has-[:checked]:bg-raised"
                  >
                    <input
                      type="radio"
                      name="payment-method"
                      value={option.token}
                      checked={method === option.token}
                      onChange={() => setMethod(option.token)}
                      className="size-4 accent-white"
                    />
                    <span className="flex-1">{option.label}</span>
                    <span className="text-label text-muted">{option.outcome}</span>
                  </label>
                ))}
              </div>
            </fieldset>

            {notice ? (
              <div className="mt-4">
                <Notice tone={notice.tone} traceId={notice.traceId}>
                  {notice.text}
                </Notice>
              </div>
            ) : null}

            <div className="mt-5 grid gap-2.5">
              <button type="button" className="btn btn-primary btn-large" onClick={pay} disabled={busy}>
                {busy ? "Working…" : `Pay ${formatMoney(hold.totalPrice, currency)}`}
              </button>
              <button type="button" className="btn btn-secondary" onClick={release} disabled={busy}>
                Change seats
              </button>
            </div>
          </>
        ) : (
          <>
            <h2 className="text-section font-semibold">Your seats</h2>
            {picked.length === 0 ? (
              <p className="mt-2 text-muted">
                Pick up to {MAX_SEATS} seats on the map. They are held for you for a few minutes while you pay.
              </p>
            ) : (
              <OrderLines seats={picked} totalCents={pickedTotal} currency={currency} />
            )}

            {notice ? (
              <div className="mt-4">
                <Notice tone={notice.tone} traceId={notice.traceId}>
                  {notice.text}
                </Notice>
              </div>
            ) : null}

            <div className="mt-5 grid gap-2.5">
              {canHold ? (
                <button
                  type="button"
                  className="btn btn-primary btn-large"
                  onClick={holdPicked}
                  disabled={busy || picked.length === 0}
                >
                  {busy
                    ? "Holding…"
                    : picked.length === 0
                      ? "Hold seats"
                      : `Hold ${picked.length === 1 ? "1 seat" : `${picked.length} seats`} · ${formatCents(pickedTotal, currency)}`}
                </button>
              ) : (
                <>
                  <Link href={loginHref} className="btn btn-primary btn-large">
                    Log in to book
                  </Link>
                  <p className="text-label text-muted">
                    New here?{" "}
                    <Link href={loginHref.replace("/login", "/register")} className="link">
                      Create an account
                    </Link>
                  </p>
                </>
              )}
            </div>
          </>
        )}
      </section>
    </div>
  );
}

const OFFLINE = {
  tone: "error" as const,
  text: "The request did not get through. Check your connection and try again.",
};

const PAYMENT_UNKNOWN = {
  tone: "error" as const,
  text: "We could not confirm the payment: the connection dropped. Press Pay again. You will not be charged twice.",
};

/** The seats of an order, row by row, and what they come to. */
function OrderLines({
  seats,
  totalCents: total,
  currency,
}: {
  seats: { seatId: number; row: number; number: number; category: string; price: number }[];
  totalCents: number;
  currency: string;
}) {
  return (
    <>
      <p className="mt-2 text-muted">{describeSeats(seats)}</p>
      <ul className="mt-3">
        {seats.map((seat) => (
          <li
            key={seat.seatId}
            className="flex items-baseline justify-between gap-4 border-t border-rule py-2 first:border-t-0"
          >
            <span>
              Row {seat.row}, seat {seat.number}
              <span className="ml-2 text-label text-muted">{seat.category === "VIP" ? "VIP" : "Standard"}</span>
            </span>
            <span className="num">{formatMoney(seat.price, currency)}</span>
          </li>
        ))}
      </ul>
      <p className="flex items-baseline justify-between gap-4 border-t border-rule pt-3 font-medium">
        <span>Total</span>
        <span className="num text-figure text-accent">{formatCents(total, currency)}</span>
      </p>
    </>
  );
}
