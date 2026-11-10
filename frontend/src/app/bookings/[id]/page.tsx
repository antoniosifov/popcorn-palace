import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { Suspense } from "react";

import { cancelMyBooking } from "@/actions/checkout";
import { ConfirmButton } from "@/components/confirm-button";
import { Poster } from "@/components/poster";
import { getBooking } from "@/lib/api/bookings";
import { ApiError } from "@/lib/api/client";
import type { Booking } from "@/lib/api/types";
import { currentUser } from "@/lib/auth";
import { loginPath } from "@/lib/paths";
import { bookingReference, bookingStatus, canCancel } from "@/lib/booking-status";
import { cinemaZone, currency as defaultCurrency } from "@/lib/config";
import { formatMoney } from "@/lib/money";
import { describeSeats } from "@/lib/seatmap";
import { sessionToken } from "@/lib/session";
import { formatDateTime, formatDay, formatTime } from "@/lib/time";
import { requestTime } from "@/lib/request-time";

export const metadata: Metadata = { title: "Your booking" };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default function BookingPage({ params }: PageProps<"/bookings/[id]">) {
  return (
    <Suspense fallback={<BookingSkeleton />}>
      <BookingContent params={params} />
    </Suspense>
  );
}

async function BookingContent({ params }: Pick<PageProps<"/bookings/[id]">, "params">) {
  const { id } = await params;
  if (!UUID.test(id)) {
    notFound();
  }
  const token = await sessionToken();
  if (!token) {
    redirect(loginPath(`/bookings/${id}`));
  }

  let booking: Booking;
  try {
    booking = await getBooking(id, token);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      // Not there, or somebody else's: the API gives the same answer for both.
      notFound();
    }
    if (error instanceof ApiError && error.status === 401) {
      redirect(loginPath(`/bookings/${id}`));
    }
    throw error;
  }
  const user = await currentUser();

  const zone = cinemaZone();
  const now = await requestTime();
  const status = bookingStatus(booking, now);
  const currency = booking.payment?.currency ?? defaultCurrency();
  const cancelled = booking.status === "CANCELLED";

  return (
    <div className="column page-top">
      <p className="mb-4 text-label">
        <Link href="/bookings" className="link-quiet">
          My bookings
        </Link>
      </p>

      <header className="mb-6 flex flex-wrap items-center gap-5">
        <Poster
          title={booking.movieTitle}
          url={booking.moviePosterUrl}
          sizes="72px"
          className="w-[72px] flex-none"
          eager
        />
        <div className="min-w-0 flex-1">
          <h1 className="display display-row">
            <Link href={`/movies/${booking.movieId}`} className="hover:text-accent-strong">
              {booking.movieTitle}
            </Link>
          </h1>
          <p className="mt-2 text-muted">
            {formatDay(booking.startTime, zone)} · <span className="num">{formatTime(booking.startTime, zone)}</span> ·{" "}
            {booking.hallName}
          </p>
        </div>
        <span className={`pill pill-${status.tone}`}>{status.text}</span>
      </header>

      <div className="grid items-start gap-3.5 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <section className="card" aria-labelledby="tickets-heading">
          <h2 id="tickets-heading" className="text-section font-semibold">
            {cancelled ? "Tickets (cancelled)" : booking.seats.length === 1 ? "Your ticket" : "Your tickets"}
          </h2>
          <p className={`mt-2 text-figure font-medium ${cancelled ? "text-muted line-through" : "text-accent"}`}>
            {describeSeats(booking.seats)}
          </p>
          <p className="mt-1 text-muted">
            {booking.hallName}, {formatDateTime(booking.startTime, zone)}
          </p>

          <table className="table mt-5">
            <thead>
              <tr>
                <th scope="col">Seat</th>
                <th scope="col">Category</th>
                <th scope="col" className="n">
                  Price
                </th>
              </tr>
            </thead>
            <tbody>
              {booking.seats.map((seat) => (
                <tr key={seat.seatId}>
                  <td>
                    Row {seat.row}, seat {seat.seatNumber}
                  </td>
                  <td className="text-muted">{seat.category === "VIP" ? "VIP" : "Standard"}</td>
                  <td className="n">{formatMoney(seat.price, currency)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={2} className="font-medium">
                  Total
                </td>
                <td className="n font-medium">{formatMoney(booking.totalPrice, currency)}</td>
              </tr>
            </tfoot>
          </table>

          {!cancelled && user ? (
            <p className="mt-4 text-label text-muted">The tickets were also emailed to {user.email}.</p>
          ) : null}
        </section>

        <section className="card" aria-labelledby="details-heading">
          <h2 id="details-heading" className="text-section font-semibold">
            Booking
          </h2>
          <dl className="mt-3 grid gap-0">
            <Detail label="Reference" value={<span className="num">{bookingReference(booking)}</span>} />
            <Detail label="Booked" value={formatDateTime(booking.createdAt, zone)} />
            {booking.cancelledAt ? (
              <Detail label="Cancelled" value={formatDateTime(booking.cancelledAt, zone)} />
            ) : null}
            <Detail label="Payment" value={booking.payment ? paymentWords(booking.payment.status) : "–"} />
            <Detail
              label={cancelled ? "Refund" : "Paid"}
              value={
                booking.payment ? <span className="num">{formatMoney(booking.payment.amount, currency)}</span> : "–"
              }
            />
          </dl>

          {canCancel(booking, now) ? (
            <div className="mt-5">
              <ConfirmButton
                label="Cancel booking"
                question={`Cancel this booking? The seats go back on sale and ${formatMoney(booking.totalPrice, currency)} is refunded.`}
                confirmLabel="Cancel and refund"
                action={cancelMyBooking.bind(null, booking.id)}
              />
            </div>
          ) : !cancelled ? (
            <p className="mt-5 text-label text-muted">
              The showtime has started, so this booking can no longer be cancelled.
            </p>
          ) : null}
        </section>
      </div>
    </div>
  );
}

function Detail({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-4 border-t border-rule py-2.5 first:border-t-0 first:pt-0">
      <dt className="text-muted">{label}</dt>
      <dd className="text-right">{value}</dd>
    </div>
  );
}

function paymentWords(status: NonNullable<Booking["payment"]>["status"]): string {
  switch (status) {
    case "SUCCEEDED":
      return "Paid";
    case "REFUND_PENDING":
      return "Refund on its way";
    case "REFUNDED":
      return "Refunded";
    case "FAILED":
      return "Failed";
    case "PENDING":
      return "Processing";
  }
}

function BookingSkeleton() {
  return (
    <div className="column page-top">
      <div className="skeleton mb-4 h-5 w-28" />
      <div className="mb-6 flex items-center gap-5">
        <div className="skeleton h-[108px] w-[72px] flex-none" />
        <div className="grid flex-1 gap-2">
          <div className="skeleton h-7 w-64 max-w-full" />
          <div className="skeleton h-5 w-80 max-w-full" />
        </div>
      </div>
      <div className="grid gap-3.5 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div className="card h-[280px]" />
        <div className="card h-[220px]" />
      </div>
    </div>
  );
}
