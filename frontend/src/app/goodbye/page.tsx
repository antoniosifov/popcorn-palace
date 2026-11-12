import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Account deleted",
  robots: { index: false },
};

/**
 * Where somebody lands after deleting their account. It knows nothing about
 * them, on purpose: by now there is nothing left to know.
 */
export default function GoodbyePage() {
  return (
    <div className="column page-top">
      <div className="mx-auto w-full max-w-[440px] md:pt-8">
        <h1 className="display display-head">Your account is deleted</h1>
        <p className="mt-3 text-muted">
          Your email, your name and your way of signing in are erased, and this browser is logged out. Bookings for
          showtimes that had not begun were cancelled and refunded. One last email says the same.
        </p>
        <p className="mt-7">
          <Link href="/" className="btn btn-primary btn-large">
            Back to the programme
          </Link>
        </p>
      </div>
    </div>
  );
}
