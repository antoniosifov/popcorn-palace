import Link from "next/link";
import type { ReactNode } from "react";

/**
 * The end of every page, set the way a film ends: the back row of the hall,
 * the credits, and the name of the house once more.
 *
 * The credits are the practical facts (where the programme is, where the
 * tickets are, whose pictures these are, who made this), each as a role and a
 * name on either side of one axis, the way a credit roll sets them.
 */
export function SiteFooter() {
  return (
    <footer className="mt-32 flex-none bg-surface md:mt-48">
      <div className="column pb-10 pt-14 md:pb-12 md:pt-20">
        <BackRow />

        <div className="mt-14 grid gap-x-16 gap-y-12 md:mt-24 lg:grid-cols-2">
          <p className="copy rise max-w-[20ch] text-[1.5rem] leading-[1.25] lg:text-[2rem] lg:leading-[1.2]">
            The lights come up. The next showing is already on sale.
          </p>

          <dl className="credits rise max-w-[32rem] lg:max-w-none">
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
        <div className="@container mt-16 md:mt-28">
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

/** How many seats the back row has on a wide screen; a phone shows the first half of them. */
const SEATS = 24;
/** The one that is yours, as on the seat map and in the mark. */
const YOURS = 7;

/**
 * The back row of the hall, across the whole page, with one seat lit. It is
 * the mark of the site at the size of the seat map, not a divider: a picture,
 * hidden from screen readers.
 */
function BackRow() {
  return (
    <div className="back-row rise" aria-hidden="true">
      {Array.from({ length: SEATS }, (_, seat) => (
        <span key={seat} className={seat === YOURS ? "is-yours" : undefined} />
      ))}
    </div>
  );
}
