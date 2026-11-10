import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { Poster } from "@/components/poster";
import { Empty } from "@/components/ui";
import { getMovie, listShowtimes } from "@/lib/api/catalog";
import { ApiError } from "@/lib/api/client";
import type { Movie, Showtime } from "@/lib/api/types";
import { cinemaZone, currency } from "@/lib/config";
import { formatMoney } from "@/lib/money";
import { dayKey, formatDuration, formatTime, relativeDayLabel, type DayKey } from "@/lib/time";
import { requestTime } from "@/lib/request-time";

export default function MoviePage({ params }: PageProps<"/movies/[id]">) {
  return (
    <Suspense fallback={<MovieSkeleton />}>
      <MovieContent params={params} />
    </Suspense>
  );
}

async function MovieContent({ params }: Pick<PageProps<"/movies/[id]">, "params">) {
  const { id } = await params;
  const movieId = Number(id);
  if (!Number.isSafeInteger(movieId) || movieId <= 0) {
    notFound();
  }

  let movie: Movie;
  try {
    movie = await getMovie(movieId);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
    throw error;
  }
  const now = await requestTime();
  const showtimes = await listShowtimes({ movieId, from: now });

  const zone = cinemaZone();
  const today = dayKey(now, zone);
  const next = showtimes[0];
  const byDay = new Map<DayKey, Showtime[]>();
  for (const showtime of showtimes) {
    const day = dayKey(showtime.startTime, zone);
    byDay.set(day, [...(byDay.get(day) ?? []), showtime]);
  }

  return (
    <>
      <title>{`${movie.title} · Popcorn Palace`}</title>
      <p className="mb-3 text-label">
        <Link href="/" className="link-quiet">
          Now showing
        </Link>
      </p>

      <header className="card mb-3.5 flex flex-wrap items-center gap-5">
        <Poster title={movie.title} size="large" />
        <div className="min-w-0 flex-1">
          <h1 className="text-title font-semibold tracking-[-0.01em]">{movie.title}</h1>
          <p className="mt-1 text-muted">
            {movie.genre} · {formatDuration(movie.durationMinutes)} · {movie.releaseYear}
          </p>
          <p className="mt-4 text-label text-muted">Rated</p>
          <p className="num text-figure font-medium">
            {movie.rating.toFixed(1)}
            <span className="ml-1 text-label font-normal text-muted">of 10</span>
          </p>
        </div>
        {next ? (
          // The answer to "when can I see it" and the way there, beside the movie it is about.
          <div className="w-full md:w-auto md:text-right">
            <p className="text-label text-muted">Next showing</p>
            <p className="mt-0.5 text-section font-semibold">
              {relativeDayLabel(dayKey(next.startTime, zone), today)},{" "}
              <span className="num">{formatTime(next.startTime, zone)}</span>
            </p>
            <p className="text-muted">{next.hallName}</p>
            <Link href={`/showtimes/${next.id}`} prefetch={false} className="btn btn-primary mt-3 w-full md:w-auto">
              Choose seats
            </Link>
          </div>
        ) : null}
      </header>

      <section className="card" aria-labelledby="showtimes-heading">
        <h2 id="showtimes-heading" className="mb-4 text-section font-semibold">
          Showtimes
        </h2>
        {byDay.size === 0 ? (
          <Empty>No showtimes are scheduled for this movie right now.</Empty>
        ) : (
          <ul>
            {[...byDay.entries()].map(([day, times]) => (
              <li
                key={day}
                className="grid gap-x-6 gap-y-2.5 border-t border-rule py-3.5 first:border-t-0 first:pt-0 last:pb-0 md:grid-cols-[150px_minmax(0,1fr)] md:items-center"
              >
                <h3 className="font-medium">{relativeDayLabel(day, today)}</h3>
                <ul className="flex flex-wrap gap-2">
                  {times.map((showtime) => {
                    const prices = Object.values(showtime.prices);
                    return (
                      <li key={showtime.id}>
                        <Link
                          href={`/showtimes/${showtime.id}`}
                          prefetch={false}
                          className="btn btn-secondary h-10 gap-2.5"
                        >
                          <span className="num font-semibold">{formatTime(showtime.startTime, zone)}</span>
                          <span className="font-normal text-muted">{showtime.hallName}</span>
                          {prices.length ? (
                            <span className="num font-normal text-muted">
                              from {formatMoney(Math.min(...prices), currency())}
                            </span>
                          ) : null}
                        </Link>
                      </li>
                    );
                  })}
                </ul>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}

function MovieSkeleton() {
  return (
    <>
      <div className="skeleton mb-3 h-[18px] w-28" />
      <div className="card mb-3.5 flex items-center gap-5">
        <div className="skeleton h-[180px] w-[120px] flex-none" />
        <div className="grid flex-1 gap-2">
          <div className="skeleton h-7 w-64 max-w-full" />
          <div className="skeleton h-5 w-72 max-w-full" />
        </div>
      </div>
      <div className="card h-[200px]" />
    </>
  );
}
