import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Suspense } from "react";

import { Poster } from "@/components/poster";
import { Empty, TitleRow } from "@/components/ui";
import { listMyBookings } from "@/lib/api/bookings";
import { ApiError } from "@/lib/api/client";
import type { Booking } from "@/lib/api/types";
import { loginPath } from "@/lib/paths";
import { bookingGroup, bookingStatus, type BookingGroup } from "@/lib/booking-status";
import { cinemaZone, currency } from "@/lib/config";
import { formatMoney } from "@/lib/money";
import { describeSeats } from "@/lib/seatmap";
import { sessionToken } from "@/lib/session";
import { formatDay, formatTime } from "@/lib/time";
import { requestTime } from "@/lib/request-time";

export const metadata: Metadata = { title: "My bookings" };

const GROUPS: { key: BookingGroup; label: string; empty: string }[] = [
  { key: "upcoming", label: "Upcoming", empty: "No tickets for upcoming showtimes." },
  { key: "past", label: "Seen", empty: "Nothing here yet: showtimes you went to are kept here." },
  { key: "cancelled", label: "Cancelled", empty: "No cancelled bookings." },
];

export default function BookingsPage({ searchParams }: PageProps<"/bookings">) {
  return (
    <>
      <TitleRow title="My bookings" />
      <Suspense fallback={<BookingsSkeleton />}>
        <Bookings searchParams={searchParams} />
      </Suspense>
    </>
  );
}

async function Bookings({ searchParams }: Pick<PageProps<"/bookings">, "searchParams">) {
  const { show } = await searchParams;
  const token = await sessionToken();
  if (!token) {
    redirect(loginPath("/bookings"));
  }

  let bookings: Booking[];
  try {
    bookings = await listMyBookings(token);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      // The token ran out since the cookie was set.
      redirect(loginPath("/bookings"));
    }
    throw error;
  }

  const now = await requestTime();
  const byGroup = new Map<BookingGroup, Booking[]>(GROUPS.map((group) => [group.key, []]));
  for (const booking of bookings) {
    byGroup.get(bookingGroup(booking, now))?.push(booking);
  }
  // Upcoming showtimes read soonest first; what is over reads newest first.
  byGroup.get("upcoming")?.sort((a, b) => a.startTime.localeCompare(b.startTime));
  byGroup.get("past")?.sort((a, b) => b.startTime.localeCompare(a.startTime));

  const selected = GROUPS.find((group) => group.key === show) ?? GROUPS[0];
  const rows = byGroup.get(selected.key) ?? [];
  const zone = cinemaZone();

  return (
    <>
      <nav aria-label="Bookings" className="mb-3.5 grid grid-cols-3 gap-2">
        {GROUPS.map((group) => {
          const current = group.key === selected.key;
          return (
            <Link
              key={group.key}
              href={group.key === "upcoming" ? "/bookings" : `/bookings?show=${group.key}`}
              aria-current={current ? "page" : undefined}
              className={`rounded-inner border-[1.5px] px-3 py-2.5 transition-colors duration-150 ${
                current ? "border-accent bg-accent-tint" : "border-transparent bg-surface hover:bg-well"
              }`}
            >
              <span className="block text-label text-muted">{group.label}</span>
              <span className="num block text-section font-semibold">{byGroup.get(group.key)?.length ?? 0}</span>
            </Link>
          );
        })}
      </nav>

      <section aria-label={selected.label} className="card">
        {rows.length === 0 ? (
          <Empty>
            {selected.empty}{" "}
            {selected.key === "upcoming" ? (
              <Link href="/" className="link">
                See what is showing
              </Link>
            ) : null}
          </Empty>
        ) : (
          <ul>
            {rows.map((booking) => {
              const status = bookingStatus(booking, now);
              return (
                <li key={booking.id} className="border-t border-rule first:border-t-0">
                  <Link
                    href={`/bookings/${booking.id}`}
                    className="group grid grid-cols-[auto_minmax(0,1fr)] items-center gap-x-4 gap-y-2 py-4 md:grid-cols-[auto_minmax(0,1fr)_auto_112px]"
                  >
                    <Poster title={booking.movieTitle} />
                    <div className="min-w-0">
                      <h2 className="truncate text-section font-semibold group-hover:underline group-hover:underline-offset-4">
                        {booking.movieTitle}
                      </h2>
                      <p className="mt-0.5 text-muted">
                        {formatDay(booking.startTime, zone)} ·{" "}
                        <span className="num">{formatTime(booking.startTime, zone)}</span> · {booking.hallName}
                      </p>
                      <p className="mt-0.5 text-label text-muted">{describeSeats(booking.seats)}</p>
                    </div>
                    <span className={`pill pill-${status.tone} col-start-2 justify-self-start md:col-start-auto`}>
                      {status.text}
                    </span>
                    <span className="num col-start-2 font-medium md:col-start-auto md:text-right">
                      {formatMoney(booking.totalPrice, booking.payment?.currency ?? currency())}
                    </span>
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </>
  );
}

function BookingsSkeleton() {
  return (
    <>
      <div className="mb-3.5 grid grid-cols-3 gap-2">
        {GROUPS.map((group) => (
          <div key={group.key} className="rounded-inner bg-surface px-3 py-2.5">
            <span className="block text-label text-muted">{group.label}</span>
            <span className="skeleton mt-1 block h-5 w-6" />
          </div>
        ))}
      </div>
      <div className="card">
        {Array.from({ length: 3 }, (_, index) => (
          <div key={index} className="flex items-center gap-4 border-t border-rule py-4 first:border-t-0">
            <div className="skeleton h-[75px] w-[50px] flex-none" />
            <div className="grid flex-1 gap-2">
              <div className="skeleton h-5 w-56 max-w-full" />
              <div className="skeleton h-4 w-72 max-w-full" />
            </div>
          </div>
        ))}
      </div>
    </>
  );
}
