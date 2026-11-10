"use server";

import { refresh } from "next/cache";

import { cancelBooking, confirmHold, createHold, releaseHold } from "@/lib/api/bookings";
import type { Booking, Hold } from "@/lib/api/types";
import type { ActionResult } from "@/lib/errors";
import { PAYMENT_METHODS } from "@/lib/payment";
import { MAX_SEATS } from "@/lib/seatmap";

import { asUser, failure, INVALID_INPUT, isPositiveInteger, isUuid } from "./support";

/** Reserves the picked seats for the customer for a few minutes: all of them or none. */
export async function holdSeats(showtimeId: number, seatIds: number[]): Promise<ActionResult<Hold>> {
  if (
    !isPositiveInteger(showtimeId) ||
    !Array.isArray(seatIds) ||
    seatIds.length === 0 ||
    seatIds.length > MAX_SEATS ||
    !seatIds.every(isPositiveInteger)
  ) {
    return failure(INVALID_INPUT);
  }
  return asUser((token) => createHold(showtimeId, seatIds, token));
}

/** Gives the seats back, so the customer can pick again. */
export async function releaseSeats(showtimeId: number, holdId: string): Promise<ActionResult<undefined>> {
  if (!isPositiveInteger(showtimeId) || !isUuid(holdId)) {
    return failure(INVALID_INPUT);
  }
  return asUser(async (token) => {
    await releaseHold(showtimeId, holdId, token);
    return undefined;
  });
}

/** The API's rule for the key: 8-100 characters of a safe alphabet. */
const IDEMPOTENCY_KEY = /^[A-Za-z0-9_.:-]{8,100}$/;

/**
 * Pays for the hold and returns the booking. The browser makes up the
 * idempotency key once per attempt and sends the same one when it repeats a
 * request whose answer it never saw, so a retry cannot charge twice.
 */
export async function payForHold(
  showtimeId: number,
  holdId: string,
  paymentToken: string,
  idempotencyKey: string,
): Promise<ActionResult<Booking>> {
  if (
    !isPositiveInteger(showtimeId) ||
    !isUuid(holdId) ||
    !PAYMENT_METHODS.some((method) => method.token === paymentToken) ||
    typeof idempotencyKey !== "string" ||
    !IDEMPOTENCY_KEY.test(idempotencyKey)
  ) {
    return failure(INVALID_INPUT);
  }
  return asUser((token) => confirmHold(showtimeId, holdId, paymentToken, idempotencyKey, token));
}

/** Cancels a booking and refunds it, then shows the booking as it is now. */
export async function cancelMyBooking(bookingId: string): Promise<ActionResult<Booking>> {
  if (!isUuid(bookingId)) {
    return failure(INVALID_INPUT);
  }
  const result = await asUser((token) => cancelBooking(bookingId, token));
  if (result.ok) {
    refresh();
  }
  return result;
}
