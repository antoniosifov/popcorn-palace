import type { Metadata, Viewport } from "next";
import { Inter } from "next/font/google";
import Link from "next/link";
import { Suspense } from "react";

import { Wordmark } from "@/components/brand";
import { Nav } from "@/components/nav";
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
  description: "Pick a movie, choose your seats on a live seat map and book them.",
};

export const viewport: Viewport = {
  themeColor: "#000000",
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
    <nav aria-label="Main" className="hidden flex-1 items-center gap-6 md:flex">
      <Link href="/" className="text-body font-semibold text-muted">
        Now showing
      </Link>
      <Link href="/bookings" className="text-body font-semibold text-muted">
        My bookings
      </Link>
    </nav>
  );
}

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={inter.variable}>
      <body className="flex min-h-svh flex-col">
        <a
          href="#main"
          className="btn btn-primary absolute left-4 top-1.5 z-30 -translate-y-16 focus-visible:translate-y-0"
        >
          Skip to content
        </a>
        <header className="column flex h-12 flex-none items-center gap-8">
          <Wordmark />
          <Suspense fallback={<NavPlaceholder />}>
            <AccountNav />
          </Suspense>
        </header>
        <main id="main" className="column flex-1 pb-20 pt-6">
          {children}
        </main>
        <footer className="column flex-none pb-8 text-small text-faint">
          <p>
            Popcorn Palace is a portfolio project. Payments run in a sandbox: no real money moves and no card details
            are asked for.
          </p>
        </footer>
      </body>
    </html>
  );
}
