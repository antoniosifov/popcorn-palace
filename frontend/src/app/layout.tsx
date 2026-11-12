import type { Metadata, Viewport } from "next";
import { Inter } from "next/font/google";
import { headers } from "next/headers";
import Link from "next/link";
import { Suspense } from "react";

import { Wordmark } from "@/components/brand";
import { Nav } from "@/components/nav";
import { Reveals } from "@/components/reveals";
import { SiteFooter } from "@/components/site-footer";
import { currentUser } from "@/lib/auth";
import { siteOrigin } from "@/lib/site";

import "./globals.css";

const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  display: "swap",
});

/**
 * What a page says about itself, to a browser and to whatever previews a
 * shared link. The picture of that preview is opengraph-image.png next to this
 * file; its address has to be a complete one, so the address of the site is
 * read from the request (or SITE_URL), not compiled in: one image runs anywhere.
 */
export async function generateMetadata(): Promise<Metadata> {
  return {
    metadataBase: siteOrigin(process.env.SITE_URL, await headers()),
    title: { default: "Popcorn Palace", template: "%s · Popcorn Palace" },
    description: "Tonight's films, their showtimes, and a live seat map to book on.",
    openGraph: { type: "website", siteName: "Popcorn Palace" },
    twitter: { card: "summary_large_image" },
  };
}

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
        <SiteFooter />
      </body>
    </html>
  );
}
