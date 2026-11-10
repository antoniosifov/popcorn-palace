import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { Poster } from "@/components/poster";
import { SeatBooking } from "@/components/seat-booking";
import { getMyHold } from "@/lib/api/bookings";
import { getSeatMap, getShowtime } from "@/lib/api/catalog";
import { ApiError } from "@/lib/api/client";
import type { Showtime } from "@/lib/api/types";
import { loginPath } from "@/lib/paths";
import { cinemaZone, currency } from "@/lib/config";
import { sessionToken } from "@/lib/session";
import { formatDay, formatTime } from "@/lib/time";
import { requestTime } from "@/lib/request-time";

export const metadata: Metadata = { title: "Choose seats" };

export default function ShowtimePage({ params }: PageProps<"/showtimes/[id]">) {
  return (
    <Suspense fallback={<ShowtimeSkeleton />}>
      <ShowtimeContent params={params} />
    </Suspense>
  );
}

async function ShowtimeContent({ params }: Pick<PageProps<"/showtimes/[id]">, "params">) {
  const { id } = await params;
  const showtimeId = Number(id);
  if (!Number.isSafeInteger(showtimeId) || showtimeId <= 0) {
    notFound();
  }
  const token = await sessionToken();

  let showtime: Showtime;
  try {
    showtime = await getShowtime(showtimeId);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
    throw error;
  }
  // With a token the seat map marks the caller's own seats, and a hold made earlier is picked up again.
  const [map, hold] = await Promise.all([
    getSeatMap(showtimeId, token),
    token ? getMyHold(showtimeId, token).catch(() => null) : null,
  ]);

  const zone = cinemaZone();
  const started = new Date(showtime.startTime).getTime() <= (await requestTime()).getTime();

  return (
    <>
      <p className="mb-3 text-label">
        <Link href="/" className="link-quiet">
          Now showing
        </Link>
        <span className="mx-2 text-faint">/</span>
        <Link href={`/movies/${showtime.movieId}`} className="link-quiet">
          {showtime.movieTitle}
        </Link>
      </p>

      <header className="card mb-3.5 flex items-center gap-4 p-3.5">
        <Poster title={showtime.movieTitle} size="header" />
        <div className="min-w-0">
          <h1 className="truncate text-title font-semibold tracking-[-0.01em]">{showtime.movieTitle}</h1>
          <p className="mt-0.5 text-muted">
            {formatDay(showtime.startTime, zone)} · <span className="num">{formatTime(showtime.startTime, zone)}</span>
            {" to "}
            <span className="num">{formatTime(showtime.endTime, zone)}</span> · {showtime.hallName}
          </p>
        </div>
      </header>

      <SeatBooking
        // A different hold is a different checkout: start it with a clean slate.
        key={hold?.id ?? "picking"}
        showtimeId={showtimeId}
        initialMap={map}
        initialHold={hold}
        loggedIn={Boolean(token)}
        loginHref={loginPath(`/showtimes/${showtimeId}`)}
        currency={currency()}
        started={started}
      />
    </>
  );
}

function ShowtimeSkeleton() {
  return (
    <>
      <div className="skeleton mb-3 h-[18px] w-48" />
      <div className="card mb-3.5 flex items-center gap-4 p-3.5">
        <div className="skeleton h-[62px] w-[50px] flex-none" />
        <div className="grid flex-1 gap-2">
          <div className="skeleton h-7 w-64 max-w-full" />
          <div className="skeleton h-5 w-80 max-w-full" />
        </div>
      </div>
      <div className="grid gap-3.5 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div className="card h-[460px]" />
        <div className="card h-[220px]" />
      </div>
    </>
  );
}
