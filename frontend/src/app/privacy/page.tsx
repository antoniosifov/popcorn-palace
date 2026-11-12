import type { Metadata } from "next";
import Link from "next/link";
import type { ReactNode } from "react";

export const metadata: Metadata = {
  title: "Privacy",
  description: "What Popcorn Palace keeps about its visitors, where it is sent, and how to have it deleted.",
};

/**
 * What this site keeps and where it sends it. Every sentence here describes
 * what the code does; when the code changes, this page changes with it.
 */
export default function PrivacyPage() {
  return (
    <div className="column page-top">
      <div className="max-w-[68ch] md:pt-8">
        <h1 className="display display-head">Privacy</h1>
        <p className="copy mt-5 text-section md:mt-7">
          Popcorn Palace is a portfolio project by Anton Iosifov: a cinema that sells no real tickets. It keeps as
          little about you as booking a seat needs, shows no advertising and follows you nowhere. This page says what it
          keeps, where that goes, and how to have it deleted.
        </p>

        <Part title="What is kept">
          <Facts>
            <Fact name="Your account">
              Your email address, the name you gave, and your password as a one-way hash: the password itself is never
              stored. If you sign in with Google, the email, the name and the account number Google gives, and no
              password at all.
            </Fact>
            <Fact name="Your bookings">
              The showtime, the seats, the price and whether the booking was paid or cancelled.
            </Fact>
            <Fact name="Payments">
              Nothing. Payments are a sandbox: no card details are asked for and no money moves.
            </Fact>
            <Fact name="Technical records">
              How long requests took and whether they succeeded, for watching that the site works. They are shown on a
              public dashboard and carry no names, email addresses or passwords. Login attempts are counted per network
              address for one minute, to slow down guessing.
            </Fact>
          </Facts>
        </Part>

        <Part title="Where it goes">
          <Facts>
            <Fact name="The server">
              One server in Bulgaria, in the European Union, holds the database. Nothing is sold, shared for advertising
              or used to build a profile of you.
            </Fact>
            <Fact name="Email">
              Tickets, cancellations and password links are delivered by Resend, a mail service, which for that receives
              your address and the message.
            </Fact>
            <Fact name="Google">
              Only if you press &ldquo;Continue with Google&rdquo;. Google then learns that you signed in here, and this
              site receives your email, your name and your Google account number. It asks for nothing else of yours at
              Google and cannot read your mail, contacts or files.
            </Fact>
            <Fact name="What your browser fetches">
              Posters and stills come from TMDB and the display typeface from Fontshare, so those two see the request
              your browser makes for them. A trailer is fetched from YouTube only when you open it.
            </Fact>
          </Facts>
        </Part>

        <Part title="Cookies">
          <p>
            Two, both needed for the site to work and neither readable by scripts. One keeps you logged in and lasts an
            hour. The other exists for ten minutes while you sign in with Google, to make sure the answer belongs to the
            sign-in you started. There are no analytics and no advertising cookies.
          </p>
        </Part>

        <Part title="How long">
          <p>
            Your account is kept until you delete it. A password link works for thirty minutes and is stored only as a
            hash. Because this is a demonstration, the whole database may also be emptied at any time, without notice.
          </p>
        </Part>

        <Part title="Having it deleted">
          <p>
            You can delete your account yourself, on{" "}
            <Link href="/account" className="link">
              its page
            </Link>
            . Your email address, your name, your password and the connection to Google are erased at once, every
            browser is logged out, and one last email says it is done. Bookings for showtimes that have not begun are
            cancelled and refunded.
          </p>
          <p className="mt-4">
            What stays is the record of what was sold and refunded: bookings and payments with no name and no address on
            them. Emails already sent stay where they were delivered, in your mailbox and in the mail service&rsquo;s
            record of what it sent, and messages on their way between the parts of this site are gone within about two
            weeks.
          </p>
          <p className="mt-4">
            To ask what is stored about you, or to have it corrected, write to Anton through{" "}
            <a href="https://antoniosifov.com" className="link" rel="noreferrer">
              antoniosifov.com
            </a>
            .
          </p>
        </Part>

        <p className="mt-14 text-label text-muted md:mt-20">
          Last changed on 12 November 2026.{" "}
          <Link href="/" className="link-quiet underline">
            Back to the programme
          </Link>
        </p>
      </div>
    </div>
  );
}

function Part({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="mt-14 md:mt-20">
      <h2 className="display display-row">{title}</h2>
      <div className="mt-5 text-body leading-7">{children}</div>
    </section>
  );
}

function Facts({ children }: { children: ReactNode }) {
  return <dl className="grid gap-x-8 gap-y-5 md:grid-cols-[11rem_minmax(0,1fr)]">{children}</dl>;
}

function Fact({ name, children }: { name: string; children: ReactNode }) {
  return (
    <>
      <dt className="font-medium">{name}</dt>
      <dd className="text-muted md:text-ink">{children}</dd>
    </>
  );
}
