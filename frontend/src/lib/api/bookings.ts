import "server-only";

import { api, ApiError } from "./client";
import type { Booking, Hold, Page } from "./types";

/** The caller's hold for a showtime, or null when there is none (or it ran out). */
export async function getMyHold(showtimeId: number, token: string): Promise<Hold | null> {
  try {
    return await api<Hold>(`/showtimes/${showtimeId}/holds/mine`, { token });
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

export function createHold(showtimeId: number, seatIds: number[], token: string): Promise<Hold> {
  return api<Hold>(`/showtimes/${showtimeId}/holds`, { method: "POST", body: { seatIds }, token });
}

export function releaseHold(showtimeId: number, holdId: string, token: string): Promise<void> {
  return api<void>(`/showtimes/${showtimeId}/holds/${holdId}`, { method: "DELETE", token });
}

/**
 * Pays for a hold. The idempotency key names this purchase attempt: sending
 * the same key again returns the first answer instead of charging twice.
 */
export function confirmHold(
  showtimeId: number,
  holdId: string,
  paymentToken: string,
  idempotencyKey: string,
  token: string,
): Promise<Booking> {
  return api<Booking>(`/showtimes/${showtimeId}/holds/${holdId}/confirm`, {
    method: "POST",
    body: { paymentToken },
    headers: { "Idempotency-Key": idempotencyKey },
    token,
  });
}

/** The caller's bookings, newest first. A customer has a handful; read them all. */
export async function listMyBookings(token: string): Promise<Booking[]> {
  const bookings: Booking[] = [];
  for (let page = 0; page < 10; page++) {
    const result = await api<Page<Booking>>("/bookings", { token, query: { page, size: 100, sort: "createdAt,desc" } });
    bookings.push(...result.content);
    if (page + 1 >= result.totalPages) {
      break;
    }
  }
  return bookings;
}

export function getBooking(id: string, token: string): Promise<Booking> {
  return api<Booking>(`/bookings/${id}`, { token });
}

export function cancelBooking(id: string, token: string): Promise<Booking> {
  return api<Booking>(`/bookings/${id}/cancel`, { method: "POST", token });
}
