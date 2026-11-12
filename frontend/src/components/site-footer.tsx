import Link from "next/link";
import { cache, Suspense, type ReactNode } from "react";

import { atWidth, safePicture, srcSet } from "@/lib/artwork";
import { listMovies } from "@/lib/api/catalog";
import type { Movie } from "@/lib/api/types";

/**
 * The end of every page, set the way a film ends: one more still, the
 * credits, and the name of the house.
 *
 * It is the first screen in reverse. There a still fills the screen and
 * dissolves into the dark of the page below it; here the dark of the page
 * gives way to a still, with no edge between them, and what is said lies along
 * its bottom. The credits are the practical facts (where the programme is,
 * where the tickets are, whose pictures these are, who made this), each as a
 * role and a name on either side of one axis, the way a credit roll sets them.
 *
 * The still is of a film that is on the programme, so it changes with the
 * programme. Without one (an empty catalogue, an API that does not answer)
 * the footer is its words alone, as the first screen is.
 */
export function SiteFooter() {
  return (
    <footer className="closing mt-20 flex-none md:mt-28">
      <Suspense fallback={null}>
        <ClosingStill />
      </Suspense>

      <div className="column relative pb-10 pt-16 md:pb-12 md:pt-24">
        <div className="grid gap-x-16 gap-y-12 lg:grid-cols-2">
          <p className="copy rise max-w-[20ch] text-[1.5rem] leading-[1.25] lg:text-[2rem] lg:leading-[1.2]">
            The lights come up. The next showing is already on sale.
          </p>

          <dl className="credits rise max-w-[32rem] lg:max-w-none">
            <Suspense fallback={null}>
              <PictureCredit />
            </Suspense>
            <Credit role="Programme">
              <Link href="/" className="credit-link">
                Now showing
              </Link>
            </Credit>
            <Credit role="Tickets">
              <Link href="/bookings" className="credit-link">
                My bookings
              </Link>
            </Credit>
            <Credit role="Films and artwork">
              <a href="https://www.themoviedb.org" className="credit-link" rel="noreferrer">
                TMDB
              </a>
            </Credit>
            <Credit role="Payments">Sandbox, no real money</Credit>
            <Credit role="Privacy">
              <Link href="/privacy" className="credit-link">
                What this site keeps
              </Link>
            </Credit>
            <Credit role="Made by">
              <a href="https://antoniosifov.com" className="credit-link" rel="noreferrer">
                Anton Iosifov
              </a>
            </Credit>
            <Credit role="Source">
              <a href="https://github.com/antoniosifov/popcorn-palace" className="credit-link" rel="noreferrer">
                GitHub
              </a>
            </Credit>
          </dl>
        </div>

        {/* The name at the width of the page, to the letter: its size is a share of the width it is set in. */}
        <div className="@container mt-16 md:mt-24">
          <p className="display rise whitespace-nowrap text-[14.7cqw] leading-[0.86] tracking-[-0.03em]">
            Popcorn Palace
          </p>
        </div>

        <p className="mt-9 max-w-[76ch] text-label text-muted md:mt-12">
          A portfolio project. No card details are asked for. Film information and artwork come from TMDB; this product
          uses the TMDB API but is not endorsed or certified by TMDB.
        </p>
      </div>
    </footer>
  );
}

function Credit({ role, children }: { role: string; children: ReactNode }) {
  return (
    <>
      <dt>{role}</dt>
      <dd>{children}</dd>
    </>
  );
}

/**
 * The film whose still closes the page: one that is in cinemas and has a
 * still, the second most popular of them. The most popular one is usually
 * what the first screen opened with, and a page should not end on the picture
 * it began with.
 *
 * The footer is an ending, not the page: if the catalogue cannot be read it
 * goes without a picture instead of failing.
 */
const closingFilm = cache(async (): Promise<Movie | null> => {
  let movies: Movie[];
  try {
    movies = await listMovies();
  } catch {
    return null;
  }
  const withStills = movies
    .filter((movie) => safePicture(movie.backdropUrl))
    .sort(
      (a, b) => Number(b.inCinemas) - Number(a.inCinemas) || (b.popularity ?? 0) - (a.popularity ?? 0) || a.id - b.id,
    );
  return withStills[1] ?? withStills[0] ?? null;
});

async function ClosingStill() {
  const film = await closingFilm();
  const picture = safePicture(film?.backdropUrl);
  if (!film || !picture) {
    return null;
  }
  return (
    <>
      {/* Sized and served by TMDB's picture servers; see lib/artwork.ts. Far below the first screen, so fetched late. */}
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        className="closing-picture"
        src={atWidth(picture, 1280)}
        srcSet={srcSet(picture, "backdrop")}
        sizes="100vw"
        alt={`A still from ${film.title}`}
        width={1280}
        height={720}
        loading="lazy"
        decoding="async"
      />
      <div className="closing-scrim" aria-hidden="true" />
    </>
  );
}

/** Says which film the still is from, in the credits, and leads to it. */
async function PictureCredit() {
  const film = await closingFilm();
  if (!film || !safePicture(film.backdropUrl)) {
    return null;
  }
  return (
    <Credit role="In the picture">
      <Link href={`/movies/${film.id}`} className="credit-link">
        {film.title}
      </Link>
    </Credit>
  );
}
