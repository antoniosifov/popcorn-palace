import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense, type CSSProperties } from "react";

import { Poster } from "@/components/poster";
import { displaySize, HeroStill } from "@/components/still";
import { Trailer } from "@/components/trailer";
import { safeLink } from "@/lib/artwork";
import { getMovie, listMovies, listShowtimes } from "@/lib/api/catalog";
import { ApiError } from "@/lib/api/client";
import type { Movie, Showtime } from "@/lib/api/types";
import { cinemaZone, currency } from "@/lib/config";
import { formatMoney } from "@/lib/money";
import { requestTime } from "@/lib/request-time";
import { dayKey, formatDuration, formatTime, relativeDayLabel, type DayKey } from "@/lib/time";

const order = (position: number) => ({ "--order": position }) as CSSProperties;

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
  // The rest of the programme is an invitation, not the page: if it cannot be read, the film is still shown.
  const [showtimes, catalog] = await Promise.all([
    listShowtimes({ movieId, from: now }),
    listMovies().catch((): Movie[] => []),
  ]);
  const alsoShowing = pickAlsoShowing(catalog, movie);

  const zone = cinemaZone();
  const today = dayKey(now, zone);
  const next = showtimes[0];
  const trailer = safeLink(movie.trailerUrl);
  const byDay = new Map<DayKey, Showtime[]>();
  for (const showtime of showtimes) {
    const day = dayKey(showtime.startTime, zone);
    byDay.set(day, [...(byDay.get(day) ?? []), showtime]);
  }
  const facts = [movie.genre, formatDuration(movie.durationMinutes), String(movie.releaseYear)];

  return (
    <>
      <title>{`${movie.title} · Popcorn Palace`}</title>

      <section className={movie.backdropUrl ? "hero hero-short" : "hero hero-plain"} aria-label="The film">
        <HeroStill title={movie.title} url={movie.backdropUrl} />
        <div className="column relative pb-10 pt-40 md:pb-14">
          <h1 className={`display ${displaySize(movie.title)} arrive`}>{movie.title}</h1>
          <p className="copy arrive mt-5 max-w-[56ch] text-section md:mt-7" style={order(1)}>
            {facts.join(" · ")}
            {next
              ? `. Next showing ${relativeDayLabel(dayKey(next.startTime, zone), today).toLowerCase()} at ${formatTime(next.startTime, zone)} in the ${next.hallName}.`
              : ". No showtimes are scheduled right now."}
          </p>
          <div className="arrive mt-7 flex flex-wrap items-center gap-x-7 gap-y-4" style={order(2)}>
            {next ? (
              <Link href={`/showtimes/${next.id}`} prefetch={false} className="btn btn-primary btn-large">
                Choose seats
              </Link>
            ) : null}
            {trailer ? <Trailer title={movie.title} url={trailer} /> : null}
          </div>
        </div>
      </section>

      <section className="column mt-20 grid gap-x-8 gap-y-10 md:mt-32 md:grid-cols-12" aria-labelledby="about">
        <div className="rise md:col-span-4">
          <Poster
            title={movie.title}
            url={movie.posterUrl}
            sizes="(min-width: 768px) 32vw, 70vw"
            className="w-[70%] md:w-full"
            eager
          />
        </div>
        <div className="md:col-span-7 md:col-start-6">
          <h2 id="about" className="display display-head rise">
            {movie.tagline ?? "About the film"}
          </h2>
          {movie.overview ? (
            <p className="copy rise mt-7 max-w-[60ch] text-section leading-[1.6]">{movie.overview}</p>
          ) : (
            <p className="copy rise mt-7 max-w-[60ch] text-section leading-[1.6] text-muted">
              No description has been written for this film yet.
            </p>
          )}
          <dl className="rise mt-10 grid gap-x-10 gap-y-6 sm:grid-cols-2">
            {movie.director ? <Credit label="Directed by" value={movie.director} /> : null}
            {movie.starring.length > 0 ? <Credit label="Starring" value={movie.starring.join(", ")} /> : null}
            {movie.rating > 0 ? (
              <Credit
                label="Rated"
                value={
                  <>
                    <span className="num">{movie.rating.toFixed(1)}</span> out of 10
                  </>
                }
              />
            ) : null}
            <Credit label="Runs" value={formatDuration(movie.durationMinutes)} />
          </dl>
        </div>
      </section>

      <section className="column mt-28 md:mt-44" aria-labelledby="showtimes-heading">
        <h2 id="showtimes-heading" className="display display-head rise">
          Showtimes
        </h2>
        {byDay.size === 0 ? (
          <p className="rise mt-10 rounded-card bg-surface px-6 py-14 text-center text-section">
            No showtimes are scheduled for this film right now.
          </p>
        ) : (
          <ul className="mt-10 grid gap-2.5 md:mt-14">
            {[...byDay.entries()].map(([day, times]) => (
              <li
                key={day}
                className="rise grid gap-x-8 gap-y-4 rounded-card bg-surface px-6 py-6 md:grid-cols-12 md:items-center md:px-8"
              >
                <h3 className="display text-[1.75rem] md:col-span-3">{relativeDayLabel(day, today)}</h3>
                {/* The times are the product's own controls, not labels of the page. */}
                <ul data-ui className="flex flex-wrap gap-2.5 md:col-span-9">
                  {times.map((showtime) => {
                    const prices = Object.values(showtime.prices);
                    return (
                      <li key={showtime.id}>
                        <Link
                          href={`/showtimes/${showtime.id}`}
                          prefetch={false}
                          className="btn btn-secondary h-11 gap-3 px-5"
                        >
                          <span className="num text-body font-semibold">{formatTime(showtime.startTime, zone)}</span>
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

      {alsoShowing.length > 0 ? (
        <section className="column mt-28 md:mt-44" aria-labelledby="also-showing">
          <h2 id="also-showing" className="display display-head rise">
            Also showing
          </h2>
          {/* A row to swipe along on a phone, with the next poster showing at the edge; five across on a wide screen. */}
          <ul className="-mx-4 mt-10 flex snap-x gap-4 overflow-x-auto px-4 pb-2 md:mx-0 md:mt-14 md:grid md:grid-cols-5 md:gap-6 md:overflow-visible md:px-0 md:pb-0">
            {alsoShowing.map((other) => (
              <li key={other.id} className="rise w-[42%] min-w-0 flex-none snap-start md:w-auto">
                <Link href={`/movies/${other.id}`} className="group block">
                  <Poster title={other.title} url={other.posterUrl} sizes="(min-width: 768px) 220px, 42vw" />
                  <span className="mt-3 block font-medium leading-snug group-hover:underline">{other.title}</span>
                  <span className="mt-1 block text-label text-muted">{other.genre}</span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </>
  );
}

/**
 * Up to five other films to offer from a film's page: the ones in cinemas now
 * when the catalog knows which those are, most popular first, then best rated.
 */
function pickAlsoShowing(catalog: Movie[], current: Movie): Movie[] {
  const others = catalog.filter((movie) => movie.id !== current.id);
  const inCinemas = others.filter((movie) => movie.inCinemas);
  return (inCinemas.length > 0 ? inCinemas : others)
    .sort((a, b) => (b.popularity ?? 0) - (a.popularity ?? 0) || b.rating - a.rating || a.id - b.id)
    .slice(0, 5);
}

/** Who made it, how long it runs: a name for the fact and the fact, set as a pair. */
function Credit({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <dt className="text-label text-muted">{label}</dt>
      <dd className="mt-1 text-section">{value}</dd>
    </div>
  );
}

function MovieSkeleton() {
  return (
    <section className="hero hero-short" aria-hidden="true">
      <div className="column relative pb-10 pt-40 md:pb-14">
        <div className="skeleton h-[clamp(3rem,9vw,8rem)] w-[min(70%,820px)]" />
        <div className="skeleton mt-7 h-6 w-[min(80%,520px)]" />
        <div className="skeleton mt-7 h-12 w-40 rounded-full" />
      </div>
    </section>
  );
}
