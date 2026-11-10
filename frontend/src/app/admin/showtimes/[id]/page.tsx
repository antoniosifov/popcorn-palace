import { notFound } from "next/navigation";
import { Suspense } from "react";

import { ShowtimeForm, type ShowtimeValues } from "@/components/admin-forms";
import { TitleRow } from "@/components/ui";
import { getShowtime, listHalls, listMovies } from "@/lib/api/catalog";
import { ApiError } from "@/lib/api/client";
import type { Showtime } from "@/lib/api/types";
import { cinemaZone } from "@/lib/config";
import { toLocalDateTime } from "@/lib/time";

export default function EditShowtimePage({ params }: PageProps<"/admin/showtimes/[id]">) {
  return (
    <>
      <TitleRow title="Edit showtime" />
      <Suspense fallback={<div className="skeleton h-[420px] rounded-card" />}>
        <Form params={params} />
      </Suspense>
    </>
  );
}

async function Form({ params }: Pick<PageProps<"/admin/showtimes/[id]">, "params">) {
  const { id } = await params;
  const showtimeId = Number(id);
  if (!Number.isSafeInteger(showtimeId) || showtimeId <= 0) {
    notFound();
  }
  let showtime: Showtime;
  try {
    showtime = await getShowtime(showtimeId);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
    throw error;
  }
  const [movies, halls] = await Promise.all([listMovies(), listHalls()]);
  const zone = cinemaZone();
  const values: ShowtimeValues = {
    id: showtime.id,
    movieId: String(showtime.movieId),
    hallId: String(showtime.hallId),
    startTime: toLocalDateTime(showtime.startTime, zone),
    endTime: toLocalDateTime(showtime.endTime, zone),
    standardPrice: showtime.prices.STANDARD?.toFixed(2) ?? "",
    vipPrice: showtime.prices.VIP?.toFixed(2) ?? "",
  };
  return (
    <ShowtimeForm
      showtime={values}
      zone={zone}
      movies={movies.map((movie) => ({ id: movie.id, label: movie.title, durationMinutes: movie.durationMinutes }))}
      halls={halls.map((hall) => ({ id: hall.id, label: `${hall.name} (${hall.seatCount} seats)` }))}
    />
  );
}
