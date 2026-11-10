"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const TABS = [
  { href: "/admin/showtimes", label: "Showtimes" },
  { href: "/admin/movies", label: "Movies" },
  { href: "/admin/halls", label: "Halls" },
];

/** The three areas of the back office, each its own view. */
export function AdminTabs() {
  const pathname = usePathname();
  return (
    <nav aria-label="Admin" className="mb-5 flex gap-6 border-b border-rule">
      {TABS.map((tab) => {
        const current = pathname === tab.href || pathname.startsWith(tab.href + "/");
        return (
          <Link
            key={tab.href}
            href={tab.href}
            aria-current={current ? "page" : undefined}
            className="relative -mb-px border-b-2 border-transparent py-2.5 font-medium text-muted transition-colors duration-150 hover:text-ink aria-[current=page]:border-ink aria-[current=page]:text-ink"
          >
            {tab.label}
          </Link>
        );
      })}
    </nav>
  );
}
