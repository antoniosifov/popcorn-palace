import Link from "next/link";
import { Suspense, type CSSProperties } from "react";

import { Poster } from "@/components/poster";
import { displaySize, HeroStill } from "@/components/still";
import { listHalls, listMovies, listShowtimes } from "@/lib/api/catalog";
import type { HallSummary, Movie, SeatCategory, Showtime } from "@/lib/api/types";
import { cinemaZone, currency } from "@/lib/config";
import { formatMoney } from "@/lib/money";
import { requestTime } from "@/lib/request-time";
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

/** How many days of the programme are shown, counting today. */
const DAYS = 7;
/** Showtimes that start before this hour on the cinema's clock are matinees. */
const EVENING_FROM = 17;

/** The position of a part among the parts that arrive on the first screen. */
const order = (position: number) => ({ "--order": position }) as CSSProperties;

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
  const [movies, showtimes, halls] = await Promise.all([
    listMovies(),
    listShowtimes({ from: now, to: horizon?.to }),
    listHalls(),
  ]);

  const byDay = new Map<DayKey, Showtime[]>(days.map((day) => [day, []]));
  for (const showtime of showtimes) {
    byDay.get(dayKey(showtime.startTime, zone))?.push(showtime);
  }
  // The day asked for, or else the first day that has anything on.
  const firstBusy = days.find((day) => (byDay.get(day)?.length ?? 0) > 0) ?? today;
  const selected = typeof requested === "string" && byDay.has(requested) ? requested : firstBusy;

  const moviesById = new Map(movies.map((movie) => [movie.id, movie]));
  const programme = groupByMovie(byDay.get(selected) ?? [], moviesById);
  const featured = pickFeatured(groupByMovie(byDay.get(firstBusy) ?? [], moviesById));

  return (
    <>
      <Hero featured={featured} zone={zone} today={today} />

      <section className="column mt-24 md:mt-40" aria-labelledby="this-week">
        <div className="grid gap-x-8 gap-y-5 md:grid-cols-12 md:items-end">
          <h2 id="this-week" className="display display-head rise md:col-span-5">
            This week
          </h2>
          <p className="copy rise text-section md:col-span-6 md:col-start-7">
            {showtimes.length === 0
              ? "Nothing is scheduled for the next seven days."
              : `${count(showtimes.length, "showtime")} over the next seven days, in ${count(halls.length, "hall")}. Pick a day; every time is on the cinema's clock (${zone}).`}
          </p>
        </div>

        {/* Seven days side by side at every width: named and counted where there is room, a week strip below that. */}
        <nav aria-label="Day" className="rise mt-10 grid grid-cols-7 gap-1.5 lg:gap-2.5">
          {days.map((day) => {
            const showtimesThatDay = byDay.get(day)?.length ?? 0;
            const current = day === selected;
            const label = relativeDayLabel(day, today);
            const [weekday, dayOfMonth] = formatDayKey(day).split(" ");
            return (
              <Link
                key={day}
                href={day === firstBusy ? "/#this-week" : `/?day=${day}#this-week`}
                aria-current={current ? "page" : undefined}
                aria-label={`${label}, ${count(showtimesThatDay, "showtime")}`}
                className={`rounded-inner border-[1.5px] px-1 py-2.5 text-center transition-colors duration-150 lg:px-4 lg:py-3.5 lg:text-left ${
                  current ? "border-accent bg-accent-tint" : "border-transparent bg-surface hover:bg-well"
                }`}
              >
                <span className="block text-label text-muted lg:hidden">{weekday}</span>
                <span className="num block text-section font-semibold lg:hidden">{dayOfMonth}</span>
                <span className="hidden text-label text-muted lg:block">{label}</span>
                <span className="num hidden text-section font-semibold lg:block">
                  {showtimesThatDay}
                  <span className="ml-1.5 text-label font-normal text-muted">
                    {showtimesThatDay === 1 ? "showtime" : "showtimes"}
                  </span>
                </span>
              </Link>
            );
          })}
        </nav>

        <section aria-label={`Movies on ${relativeDayLabel(selected, today)}`} className="mt-14 md:mt-20">
          {programme.length === 0 ? (
            <p className="rounded-card bg-surface px-6 py-14 text-center text-section">
              {selected === today
                ? "Nothing more is showing today. Pick another day."
                : "Nothing is scheduled for this day."}
            </p>
          ) : (
            <ul className="grid gap-16 md:gap-24">
              {programme.map(({ movie, title, showtimes: times }, index) => (
                <li
                  key={title}
                  className="grid grid-cols-[108px_minmax(0,1fr)] gap-x-5 gap-y-6 md:grid-cols-12 md:gap-x-8"
                >
                  <MovieLink movie={movie} className="rise block md:col-span-3" label={`${title}: about the film`}>
                    <Poster
                      title={title}
                      url={movie?.posterUrl}
                      sizes="(min-width: 768px) 24vw, 108px"
                      className="w-full"
                      eager={index < 2}
                    />
                  </MovieLink>
                  <div className="rise min-w-0 md:col-span-5">
                    <h3 className="display display-row">
                      <MovieLink movie={movie} className="hover:text-accent-strong">
                        {title}
                      </MovieLink>
                    </h3>
                    {movie ? (
                      <p className="mt-3 text-label text-muted">
                        {movie.genre} · {formatDuration(movie.durationMinutes)} · {movie.releaseYear}
                        {movie.rating > 0 ? (
                          <>
                            {" · Rated "}
                            <span className="num">{movie.rating.toFixed(1)}</span>
                          </>
                        ) : null}
                      </p>
                    ) : null}
                    {movie?.overview ? (
                      <p className="copy mt-4 line-clamp-4 max-w-[58ch] md:line-clamp-3">{movie.overview}</p>
                    ) : null}
                  </div>
                  <ul className="rise col-span-2 flex flex-wrap content-start gap-2.5 md:col-span-4 md:flex-col md:flex-nowrap md:items-end">
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
      </section>

      <Tickets showtimes={showtimes} halls={halls} zone={zone} />
    </>
  );
}

type MovieProgramme = { title: string; movie: Movie | undefined; showtimes: Showtime[] };

/**
 * The first screen: the film to see tonight, as its own still, with its title
 * along the bottom edge and its next showtime as the one thing to do.
 */
function Hero({ featured, zone, today }: { featured: MovieProgramme | undefined; zone: string; today: DayKey }) {
  if (!featured) {
    return (
      <section className="hero hero-plain" aria-label="Tonight">
        <div className="column relative pb-12 pt-40">
          <h1 className="display display-short arrive">Popcorn Palace</h1>
          <p className="copy arrive mt-6 max-w-[46ch] text-section" style={order(1)}>
            Nothing is scheduled for the next seven days. The programme appears here as soon as a showtime is added.
          </p>
        </div>
      </section>
    );
  }
  const { title, movie } = featured;
  const next = featured.showtimes[0];
  const day = relativeDayLabel(dayKey(next.startTime, zone), today);
  // "Tonight" is for the evening; a matinee is "today".
  const evening = Number(formatTime(next.startTime, zone).slice(0, 2)) >= EVENING_FROM;
  const when = `${day === "Today" && evening ? "Tonight" : day} at ${formatTime(next.startTime, zone)} in the ${next.hallName}.`;
  return (
    <section className={movie?.backdropUrl ? "hero" : "hero hero-plain"} aria-label="Tonight">
      <HeroStill title={title} url={movie?.backdropUrl} />
      <div className="column relative pb-10 pt-40 md:pb-16">
        <h1 className={`display ${displaySize(title)} arrive`}>{title}</h1>
        <p className="copy arrive mt-5 max-w-[52ch] text-section md:mt-7" style={order(1)}>
          {when} {movie?.tagline ?? ""}
        </p>
        <div className="arrive mt-7 flex flex-wrap items-center gap-x-7 gap-y-4" style={order(2)}>
          <Link href={`/showtimes/${next.id}`} prefetch={false} className="btn btn-primary btn-large">
            Choose seats
          </Link>
          {movie ? (
            <Link href={`/movies/${movie.id}`} className="link">
              About the film
            </Link>
          ) : null}
        </div>
      </div>
    </section>
  );
}

/** What the first screen leads with: the film most people came for, preferring one that has a still to show. */
function pickFeatured(programme: MovieProgramme[]): MovieProgramme | undefined {
  const weight = ({ movie }: MovieProgramme) =>
    (movie?.backdropUrl ? 1_000_000 : 0) + (movie?.popularity ?? 0) * 100 + (movie?.rating ?? 0);
  return [...programme].sort((a, b) => weight(b) - weight(a))[0];
}

/** A link to a movie's page; plain content for a showtime whose movie the catalog no longer lists. */
function MovieLink({
  movie,
  className,
  label,
  children,
}: {
  movie: Movie | undefined;
  className?: string;
  label?: string;
  children: React.ReactNode;
}) {
  if (!movie) {
    return <span className={className}>{children}</span>;
  }
  return (
    <Link href={`/movies/${movie.id}`} className={className} aria-label={label}>
      {children}
    </Link>
  );
}

/** One showtime as a button: when, where, and from how much. */
function ShowtimeLink({ showtime, zone }: { showtime: Showtime; zone: string }) {
  const prices = Object.values(showtime.prices);
  const from = prices.length ? Math.min(...prices) : null;
  return (
    <Link href={`/showtimes/${showtime.id}`} prefetch={false} className="btn btn-secondary h-11 gap-3 px-5">
      <span className="num text-body font-semibold">{formatTime(showtime.startTime, zone)}</span>
      <span className="font-normal text-muted">{showtime.hallName}</span>
      {from !== null ? <span className="num font-normal text-muted">from {formatMoney(from, currency())}</span> : null}
    </Link>
  );
}

/**
 * What a ticket costs and where the films are shown, set like the card in a
 * box office window. The prices are read off the programme, not typed in.
 */
function Tickets({ showtimes, halls, zone }: { showtimes: Showtime[]; halls: HallSummary[]; zone: string }) {
  const matinee = priceRange(showtimes.filter((showtime) => hourOf(showtime, zone) < EVENING_FROM));
  const evening = priceRange(showtimes.filter((showtime) => hourOf(showtime, zone) >= EVENING_FROM));
  const groups = [
    { name: `Before ${EVENING_FROM}:00`, note: "Matinees, every day of the week.", prices: matinee },
    { name: `From ${EVENING_FROM}:00`, note: "Evening showtimes.", prices: evening },
  ].filter((group) => group.prices.length > 0);

  return (
    <section className="column mt-32 grid gap-x-8 gap-y-10 md:mt-48 md:grid-cols-12" aria-labelledby="tickets">
      <div className="md:col-span-4">
        <h2 id="tickets" className="display display-head rise">
          Tickets and halls
        </h2>
        <p className="copy rise mt-6 max-w-[36ch]">
          Seats are held for a few minutes while you pay. A booking can be cancelled, and is refunded, until the film
          starts.
        </p>
      </div>
      <div className="sheet rise px-7 py-16 md:col-span-7 md:col-start-6 md:px-14 md:py-20">
        {groups.map((group) => (
          <div key={group.name} className="mb-12">
            <h3 className="display text-[1.75rem]">{group.name}</h3>
            <p className="sheet-soft mt-1">{group.note}</p>
            <ul className="mt-5 grid gap-3">
              {group.prices.map(({ category, from, varies }) => (
                <li key={category} className="flex items-baseline justify-between gap-6 text-section">
                  <span>{category === "VIP" ? "VIP seat, the back rows" : "Standard seat"}</span>
                  <span className="num font-medium">
                    {varies ? "from " : ""}
                    {formatMoney(from, currency())}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        ))}
        <div>
          <h3 className="display text-[1.75rem]">The halls</h3>
          <ul className="mt-5 grid gap-3">
            {halls.map((hall) => (
              <li key={hall.id} className="flex items-baseline justify-between gap-6 text-section">
                <span>{hall.name}</span>
                <span className="sheet-soft num">
                  {hall.rowCount} rows, {hall.seatCount} seats
                </span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  );
}

function hourOf(showtime: Showtime, zone: string): number {
  return Number(formatTime(showtime.startTime, zone).slice(0, 2));
}

/** The lowest price of each seat category among the showtimes, and whether it is the only one. */
function priceRange(showtimes: Showtime[]): { category: SeatCategory; from: number; varies: boolean }[] {
  const categories: SeatCategory[] = ["STANDARD", "VIP"];
  return categories.flatMap((category) => {
    const prices = showtimes.map((showtime) => showtime.prices[category]).filter((price) => price !== undefined);
    if (prices.length === 0) {
      return [];
    }
    return [{ category, from: Math.min(...prices), varies: new Set(prices).size > 1 }];
  });
}

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

/** The same frame with flat bars where the values will be. Headings that are known are shown at once. */
function ProgrammeSkeleton() {
  return (
    <>
      <section className="hero" aria-hidden="true">
        <div className="column relative pb-10 pt-40 md:pb-16">
          <div className="skeleton h-[clamp(3rem,9vw,8rem)] w-[min(70%,820px)]" />
          <div className="skeleton mt-7 h-6 w-[min(80%,520px)]" />
          <div className="skeleton mt-7 h-12 w-40 rounded-full" />
        </div>
      </section>
      <section className="column mt-24 md:mt-40">
        <h2 className="display display-head">This week</h2>
        <div className="mt-10 grid grid-cols-7 gap-1.5 lg:gap-2.5">
          {Array.from({ length: DAYS }, (_, index) => (
            <div key={index} className="skeleton h-[66px] rounded-inner lg:h-[78px]" />
          ))}
        </div>
      </section>
    </>
  );
}
