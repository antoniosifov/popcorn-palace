import type { Metadata, Viewport } from "next";
import { Inter } from "next/font/google";
import Link from "next/link";
import { Suspense } from "react";

import { Wordmark } from "@/components/brand";
import { Nav } from "@/components/nav";
import { Reveals } from "@/components/reveals";
import { currentUser } from "@/lib/auth";

import "./globals.css";

const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  display: "swap",
});

export const metadata: Metadata = {
  title: { default: "Popcorn Palace", template: "%s · Popcorn Palace" },
  description: "Tonight's films, their showtimes, and a live seat map to book on.",
};

export const viewport: Viewport = {
  themeColor: "#111111",
  colorScheme: "dark",
};

/** Reads the session, so it streams in behind the bar's fallback instead of holding the page. */
async function AccountNav() {
  const user = await currentUser();
  return <Nav user={user ? { displayName: user.displayName, admin: user.role === "ADMIN" } : null} />;
}

/**
 * What the bar shows until the session has been read: the links everybody
 * has, not yet marked or personal. It is part of the prerendered shell of
 * every page, so it must not depend on the address or on who is asking.
 */
function NavPlaceholder() {
  return (
    <nav aria-label="Main" className="hidden flex-1 items-center gap-7 md:flex">
      <Link href="/" className="text-label font-medium text-muted">
        Now showing
      </Link>
      <Link href="/bookings" className="text-label font-medium text-muted">
        My bookings
      </Link>
    </nav>
  );
}

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={inter.variable}>
      <head>
        {/* Sentient, the display face, is served by Fontshare; Inter is bundled at build time. */}
        <link rel="preconnect" href="https://api.fontshare.com" />
        <link rel="preconnect" href="https://cdn.fontshare.com" crossOrigin="" />
        <link href="https://api.fontshare.com/v2/css?f[]=sentient@300,400&display=swap" rel="stylesheet" />
      </head>
      <body className="flex min-h-svh flex-col">
        <a
          href="#main"
          className="btn btn-primary absolute left-4 top-3 z-30 -translate-y-20 focus-visible:translate-y-0"
        >
          Skip to content
        </a>
        {/* The bar lies over the top of the page, so a still can begin at the very edge of the screen. */}
        <header className="absolute inset-x-0 top-0 z-20">
          <div className="column flex h-[72px] items-center gap-9">
            <Wordmark />
            <Suspense fallback={<NavPlaceholder />}>
              <AccountNav />
            </Suspense>
          </div>
        </header>
        <Reveals />
        <main id="main" className="flex-1">
          {children}
        </main>
        <footer className="mt-32 flex-none bg-surface md:mt-48">
          <div className="column pb-10 pt-16 md:pt-24">
            <p className="display rise whitespace-nowrap text-[clamp(2.25rem,11vw,12rem)] leading-[0.9]">
              Popcorn Palace
            </p>
            <div className="mt-12 grid gap-x-10 gap-y-8 md:mt-16 md:grid-cols-[minmax(0,1fr)_auto]">
              <p className="max-w-[52ch] text-muted">
                A portfolio project. Payments run in a sandbox: no real money moves and no card details are asked for.
                Film information and artwork come from TMDB; this product uses the TMDB API but is not endorsed or
                certified by TMDB.
              </p>
              <nav aria-label="Footer" className="flex flex-wrap gap-x-7 gap-y-2 md:justify-end">
                <Link href="/" className="link">
                  Now showing
                </Link>
                <Link href="/bookings" className="link">
                  My bookings
                </Link>
                <a href="https://www.themoviedb.org" className="link" rel="noreferrer">
                  TMDB
                </a>
              </nav>
            </div>
          </div>
        </footer>
      </body>
    </html>
  );
}
