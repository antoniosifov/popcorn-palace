import type { Booking } from "./api/types";

/** Where a booking stands, in the words a customer uses. */
export type BookingGroup = "upcoming" | "past" | "cancelled";

export function bookingGroup(booking: Booking, now: Date): BookingGroup {
  if (booking.status === "CANCELLED") {
    return "cancelled";
  }
  return new Date(booking.startTime).getTime() > now.getTime() ? "upcoming" : "past";
}

export type StatusLabel = { text: string; tone: "positive" | "warning" | "neutral" };

export function bookingStatus(booking: Booking, now: Date): StatusLabel {
  if (booking.status === "CANCELLED") {
    // Cancelling frees the seats at once; the money follows.
    return booking.payment?.status === "REFUND_PENDING"
      ? { text: "Cancelled, refund on its way", tone: "warning" }
      : { text: "Cancelled and refunded", tone: "neutral" };
  }
  return bookingGroup(booking, now) === "upcoming"
    ? { text: "Confirmed", tone: "positive" }
    : { text: "Seen", tone: "neutral" };
}

/** A booking can be cancelled by its owner until the showtime starts. */
export function canCancel(booking: Booking, now: Date): boolean {
  return bookingGroup(booking, now) === "upcoming";
}

/** The first block of the id: short enough to read out at the door. */
export function bookingReference(booking: Pick<Booking, "id">): string {
  return booking.id.slice(0, 8).toUpperCase();
}
