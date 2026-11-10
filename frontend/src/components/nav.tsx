"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";

import { logout } from "@/actions/auth";

export type NavUser = { displayName: string; admin: boolean };

type Item = { href: string; label: string };

function items(user: NavUser | null): Item[] {
  const list: Item[] = [
    { href: "/", label: "Now showing" },
    { href: "/bookings", label: "My bookings" },
  ];
  if (user?.admin) {
    list.push({ href: "/admin", label: "Admin" });
  }
  return list;
}

function isCurrent(pathname: string, href: string): boolean {
  if (href === "/") {
    // The programme, a movie and a showtime are all part of "Now showing".
    return pathname === "/" || pathname.startsWith("/movies") || pathname.startsWith("/showtimes");
  }
  return pathname === href || pathname.startsWith(href + "/");
}

/**
 * The links of the top bar and the account at its right end. The current
 * place is marked by brightness alone. On a phone the links fold into a sheet
 * behind a menu button.
 */
export function Nav({ user }: { user: NavUser | null }) {
  const pathname = usePathname();
  // The sheet belongs to the page it was opened on: navigating anywhere closes it.
  const [openOn, setOpenOn] = useState<string | null>(null);
  const open = openOn === pathname;
  const links = items(user);

  return (
    <>
      <nav aria-label="Main" className="hidden flex-1 items-center gap-6 md:flex">
        {links.map((item) => (
          <Link
            key={item.href}
            href={item.href}
            aria-current={isCurrent(pathname, item.href) ? "page" : undefined}
            className="text-body font-semibold text-muted transition-colors duration-150 hover:text-ink aria-[current=page]:text-ink"
          >
            {item.label}
          </Link>
        ))}
      </nav>

      <div className="hidden items-center gap-4 md:flex">
        {user ? (
          <>
            <span className="max-w-[18ch] truncate text-muted">{user.displayName}</span>
            <form action={logout}>
              <button type="submit" className="btn btn-secondary">
                Log out
              </button>
            </form>
          </>
        ) : (
          <>
            <Link href="/register" className="link-quiet font-medium">
              Create account
            </Link>
            <Link href="/login" className="btn btn-secondary">
              Log in
            </Link>
          </>
        )}
      </div>

      <button
        type="button"
        className="btn btn-secondary ml-auto md:hidden"
        aria-expanded={open}
        aria-controls="menu-sheet"
        onClick={() => setOpenOn(open ? null : pathname)}
      >
        Menu
      </button>

      {open ? (
        <div id="menu-sheet" className="fixed inset-0 z-20 overflow-y-auto bg-ground md:hidden">
          <div className="column flex h-12 items-center justify-between">
            <span className="text-[15px] font-semibold">Menu</span>
            <button type="button" className="btn btn-secondary" onClick={() => setOpenOn(null)}>
              Close
            </button>
          </div>
          <nav aria-label="Main" className="column flex flex-col pt-2">
            {links.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                aria-current={isCurrent(pathname, item.href) ? "page" : undefined}
                onClick={() => setOpenOn(null)}
                className="flex h-12 items-center border-b border-rule text-section font-semibold text-muted aria-[current=page]:text-ink"
              >
                {item.label}
              </Link>
            ))}
            <div className="flex flex-col gap-3 pt-6">
              {user ? (
                <>
                  <p className="text-muted">Logged in as {user.displayName}</p>
                  <form action={logout}>
                    <button type="submit" className="btn btn-secondary btn-large w-full">
                      Log out
                    </button>
                  </form>
                </>
              ) : (
                <>
                  <Link href="/login" className="btn btn-primary btn-large" onClick={() => setOpenOn(null)}>
                    Log in
                  </Link>
                  <Link href="/register" className="btn btn-secondary btn-large" onClick={() => setOpenOn(null)}>
                    Create account
                  </Link>
                </>
              )}
            </div>
          </nav>
        </div>
      ) : null}
    </>
  );
}
