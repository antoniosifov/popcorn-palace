import Link from "next/link";
import { Suspense } from "react";

import { Poster } from "@/components/poster";
import { Empty, TitleRow } from "@/components/ui";
import { listMovies, listShowtimes } from "@/lib/api/catalog";
import type { Movie, Showtime } from "@/lib/api/types";
import { cinemaZone, currency } from "@/lib/config";
import { formatMoney } from "@/lib/money";
import {
  addDays,
  dayBounds,
  dayKey,
  formatDayKey,
  formatDuration,
  formatTime,
  relativeDayLabel,
  type DayKey,
} from "@/lib/time";
import { requestTime } from "@/lib/request-time";

/** How many days of the programme are shown, counting today. */
const DAYS = 7;

export default function NowShowingPage({ searchParams }: PageProps<"/">) {
  return (
    <Suspense fallback={<ProgrammeSkeleton />}>
      <Programme searchParams={searchParams} />
    </Suspense>
  );
}

async function Programme({ searchParams }: Pick<PageProps<"/">, "searchParams">) {
  const { day: requested } = await searchParams;
  const zone = cinemaZone();
  const now = await requestTime();
  const today = dayKey(now, zone);
  const days = Array.from({ length: DAYS }, (_, offset) => addDays(today, offset) as DayKey);
  const horizon = dayBounds(days[DAYS - 1], zone);

  // Only what can still be booked: showtimes that have not started yet.
  const [movies, showtimes] = await Promise.all([listMovies(), listShowtimes({ from: now, to: horizon?.to })]);

  const byDay = new Map<DayKey, Showtime[]>(days.map((day) => [day, []]));
  for (const showtime of showtimes) {
    byDay.get(dayKey(showtime.startTime, zone))?.push(showtime);
  }
  // The day asked for, or else the first day that has anything on.
  const firstBusy = days.find((day) => (byDay.get(day)?.length ?? 0) > 0) ?? today;
  const selected = typeof requested === "string" && byDay.has(requested) ? requested : firstBusy;
  const ofTheDay = byDay.get(selected) ?? [];

  const moviesById = new Map(movies.map((movie) => [movie.id, movie]));
  const programme = groupByMovie(ofTheDay, moviesById);
  const next = showtimes[0];

  return (
    <>
      <TitleRow
        title="Now showing"
        sub={
          next
            ? `${count(showtimes.length, "showtime")} over the next ${DAYS} days. Next: ${next.movieTitle}, ${relativeDayLabel(dayKey(next.startTime, zone), today).toLowerCase()} at ${formatTime(next.startTime, zone)}.`
            : "Nothing is scheduled for the next 7 days."
        }
      />

      {/* Seven days side by side at every width: named and counted where there is room, a week strip on a phone. */}
      <nav aria-label="Day" className="mb-3.5 grid grid-cols-7 gap-1.5 md:gap-2">
        {days.map((day) => {
          const showtimesThatDay = byDay.get(day)?.length ?? 0;
          const current = day === selected;
          const label = relativeDayLabel(day, today);
          const [weekday, dayOfMonth] = formatDayKey(day).split(" ");
          return (
            <Link
              key={day}
              href={day === firstBusy ? "/" : `/?day=${day}`}
              aria-current={current ? "page" : undefined}
              aria-label={`${label}, ${count(showtimesThatDay, "showtime")}`}
              className={`rounded-inner border-[1.5px] px-1 py-2 text-center transition-colors duration-150 md:px-3 md:py-2.5 md:text-left ${
                current ? "border-accent bg-accent-tint" : "border-transparent bg-surface hover:bg-well"
              }`}
            >
              <span className="block text-label text-muted md:hidden">{weekday}</span>
              <span className="num block text-section font-semibold md:hidden">{dayOfMonth}</span>
              <span className="hidden text-label text-muted md:block">{label}</span>
              <span className="num hidden text-section font-semibold md:block">
                {showtimesThatDay}
                <span className="ml-1 text-label font-normal text-muted">
                  {showtimesThatDay === 1 ? "showtime" : "showtimes"}
                </span>
              </span>
            </Link>
          );
        })}
      </nav>

      <section aria-label={`Movies on ${relativeDayLabel(selected, today)}`} className="card">
        {programme.length === 0 ? (
          <Empty>
            {selected === today
              ? "Nothing more is showing today. Pick another day."
              : "Nothing is scheduled for this day."}
          </Empty>
        ) : (
          <ul>
            {programme.map(({ movie, title, showtimes: times }) => (
              <li
                key={title}
                className="grid gap-x-4 gap-y-3 border-t border-rule py-4 first:border-t-0 first:pt-0 last:pb-0 md:grid-cols-[minmax(0,1fr)_auto] md:items-center"
              >
                <div className="flex min-w-0 items-center gap-4">
                  <Poster title={title} />
                  <div className="min-w-0">
                    <h2 className="truncate text-section font-semibold">
                      {movie ? (
                        <Link href={`/movies/${movie.id}`} className="hover:underline hover:underline-offset-4">
                          {title}
                        </Link>
                      ) : (
                        title
                      )}
                    </h2>
                    {movie ? (
                      <p className="mt-0.5 text-label text-muted">
                        {movie.genre} · {formatDuration(movie.durationMinutes)} · {movie.releaseYear} · Rated{" "}
                        <span className="num">{movie.rating.toFixed(1)}</span>
                      </p>
                    ) : null}
                  </div>
                </div>
                <ul className="flex flex-wrap gap-2 md:max-w-[620px] md:justify-end">
                  {times.map((showtime) => (
                    <li key={showtime.id}>
                      <ShowtimeLink showtime={showtime} zone={zone} />
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        )}
      </section>

      <p className="mt-3 text-small text-faint">Times are on the cinema&apos;s clock ({zone}).</p>
    </>
  );
}

/** One showtime as a button: when, where, and from how much. */
function ShowtimeLink({ showtime, zone }: { showtime: Showtime; zone: string }) {
  const prices = Object.values(showtime.prices);
  const from = prices.length ? Math.min(...prices) : null;
  return (
    <Link href={`/showtimes/${showtime.id}`} prefetch={false} className="btn btn-secondary h-10 gap-2.5">
      <span className="num font-semibold">{formatTime(showtime.startTime, zone)}</span>
      <span className="font-normal text-muted">{showtime.hallName}</span>
      {from !== null ? <span className="num font-normal text-muted">from {formatMoney(from, currency())}</span> : null}
    </Link>
  );
}

type MovieProgramme = { title: string; movie: Movie | undefined; showtimes: Showtime[] };

/** The day's showtimes under their movies, in the order the movies first come up. */
function groupByMovie(showtimes: Showtime[], movies: Map<number, Movie>): MovieProgramme[] {
  const groups = new Map<number, MovieProgramme>();
  for (const showtime of showtimes) {
    const group = groups.get(showtime.movieId) ?? {
      title: showtime.movieTitle,
      movie: movies.get(showtime.movieId),
      showtimes: [],
    };
    group.showtimes.push(showtime);
    groups.set(showtime.movieId, group);
  }
  return [...groups.values()];
}

function count(value: number, noun: string): string {
  return `${value} ${value === 1 ? noun : noun + "s"}`;
}

/** The same frame with flat bars where the values will be. Labels that are known are shown at once. */
function ProgrammeSkeleton() {
  return (
    <>
      <TitleRow title="Now showing" sub={<span className="skeleton inline-block h-4 w-80 max-w-full align-middle" />} />
      <div className="mb-3.5 grid grid-cols-7 gap-1.5 md:gap-2">
        {Array.from({ length: DAYS }, (_, index) => (
          <div key={index} className="skeleton h-[62px] rounded-inner md:h-[66px]" />
        ))}
      </div>
      <div className="card">
        {Array.from({ length: 5 }, (_, index) => (
          <div
            key={index}
            className="flex items-center gap-4 border-t border-rule py-4 first:border-t-0 first:pt-0 last:pb-0"
          >
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
