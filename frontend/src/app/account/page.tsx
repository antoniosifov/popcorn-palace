import type { Metadata } from "next";
import Link from "next/link";
import { Suspense, type ReactNode } from "react";

import { DeleteAccount } from "@/components/delete-account";
import { TitleRow } from "@/components/ui";
import { requireUser } from "@/lib/auth";
import { cinemaZone } from "@/lib/config";

export const metadata: Metadata = { title: "Account" };

export default function AccountPage() {
  return (
    <div className="column page-top">
      <TitleRow title="Account" />
      <Suspense fallback={<AccountSkeleton />}>
        <Account />
      </Suspense>
    </div>
  );
}

/** What the site knows about the person, and the way to have it forgotten. */
async function Account() {
  const user = await requireUser("/account");
  const since = new Intl.DateTimeFormat("en-GB", {
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: cinemaZone(),
  }).format(new Date(user.createdAt));

  return (
    <div className="grid items-start gap-3.5 lg:grid-cols-2">
      <section className="card" aria-labelledby="details-heading">
        <h2 id="details-heading" className="text-section font-semibold">
          Details
        </h2>
        <dl className="mt-3 grid gap-0">
          <Detail label="Name" value={user.displayName} />
          <Detail label="Email" value={<span className="break-all">{user.email}</span>} />
          <Detail label="Signing in" value={user.hasPassword ? "With a password" : "With Google"} />
          <Detail label="Since" value={since} />
        </dl>
        <p className="mt-5 text-label text-muted">
          Your tickets are under{" "}
          <Link href="/bookings" className="link">
            My bookings
          </Link>
          .
        </p>
      </section>

      <section className="card" aria-labelledby="delete-heading">
        <h2 id="delete-heading" className="text-section font-semibold">
          Delete account
        </h2>
        {user.role === "ADMIN" ? (
          <p className="mt-1 text-muted">An administrator account cannot be deleted.</p>
        ) : (
          <>
            <p className="mt-1 text-muted">
              Erases your email, your name and your way of signing in, and logs you out everywhere. Bookings for
              showtimes that have not begun are cancelled and refunded.{" "}
              <Link href="/privacy" className="link">
                What stays
              </Link>
            </p>
            <div className="mt-5">
              <DeleteAccount hasPassword={user.hasPassword} />
            </div>
          </>
        )}
      </section>
    </div>
  );
}

function Detail({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-4 border-t border-rule py-2.5 first:border-t-0 first:pt-0">
      <dt className="text-muted">{label}</dt>
      <dd className="text-right">{value}</dd>
    </div>
  );
}

function AccountSkeleton() {
  return (
    <div className="grid items-start gap-3.5 lg:grid-cols-2">
      <div className="card">
        <div className="skeleton h-5 w-24" />
        <div className="skeleton mt-4 h-32" />
      </div>
      <div className="card">
        <div className="skeleton h-5 w-36" />
        <div className="skeleton mt-4 h-20" />
      </div>
    </div>
  );
}
