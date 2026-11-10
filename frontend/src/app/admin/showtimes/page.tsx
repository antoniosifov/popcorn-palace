import Link from "next/link";
import { Suspense } from "react";

import { removeShowtime } from "@/actions/admin";
import { ConfirmButton } from "@/components/confirm-button";
import { Empty, TitleRow } from "@/components/ui";
import { listShowtimes } from "@/lib/api/catalog";
import { cinemaZone, currency } from "@/lib/config";
import { formatMoney } from "@/lib/money";
import { requestTime } from "@/lib/request-time";
import { formatDay, formatTime } from "@/lib/time";

export default function AdminShowtimesPage() {
  return (
    <>
      <TitleRow
        title="Showtimes"
        sub="What is still to come, soonest first."
        action={
          <Link href="/admin/showtimes/new" className="btn btn-primary">
            Schedule a showtime
          </Link>
        }
      />
      <section className="card">
        <Suspense fallback={<div className="skeleton h-[280px]" />}>
          <Showtimes />
        </Suspense>
      </section>
    </>
  );
}

async function Showtimes() {
  const showtimes = await listShowtimes({ from: await requestTime() });
  if (showtimes.length === 0) {
    return <Empty>Nothing is scheduled. Schedule the first showtime.</Empty>;
  }
  const zone = cinemaZone();
  const money = (amount: number | undefined) => (amount === undefined ? "–" : formatMoney(amount, currency()));
  return (
    <div className="overflow-x-auto">
      <p className="mb-2 text-label text-muted">
        <span className="num">{showtimes.length}</span> upcoming
      </p>
      <table className="table min-w-[820px]">
        <thead>
          <tr>
            <th scope="col">Starts</th>
            <th scope="col">Movie</th>
            <th scope="col">Hall</th>
            <th scope="col" className="n">
              Ends
            </th>
            <th scope="col" className="n">
              Standard
            </th>
            <th scope="col" className="n">
              VIP
            </th>
            <th scope="col" className="n">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {showtimes.map((showtime) => (
            <tr key={showtime.id}>
              <td className="whitespace-nowrap">
                {formatDay(showtime.startTime, zone)},{" "}
                <span className="num">{formatTime(showtime.startTime, zone)}</span>
              </td>
              <td className="font-medium">
                <Link href={`/showtimes/${showtime.id}`} className="hover:underline hover:underline-offset-4">
                  {showtime.movieTitle}
                </Link>
              </td>
              <td className="text-muted">{showtime.hallName}</td>
              <td className="n text-muted">{formatTime(showtime.endTime, zone)}</td>
              <td className="n">{money(showtime.prices.STANDARD)}</td>
              <td className="n">{money(showtime.prices.VIP)}</td>
              <td className="n whitespace-nowrap">
                <Link href={`/admin/showtimes/${showtime.id}`} className="link mr-4">
                  Edit
                </Link>
                <ConfirmButton
                  look="text"
                  label="Delete"
                  question={`Delete ${showtime.movieTitle} at ${formatTime(showtime.startTime, zone)}?`}
                  confirmLabel="Delete showtime"
                  action={removeShowtime.bind(null, showtime.id)}
                />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
