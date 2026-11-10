import { Suspense } from "react";

import { ShowtimeForm } from "@/components/admin-forms";
import { TitleRow } from "@/components/ui";
import { listHalls, listMovies } from "@/lib/api/catalog";
import { cinemaZone } from "@/lib/config";

export default function NewShowtimePage() {
  return (
    <>
      <TitleRow title="Schedule a showtime" />
      <Suspense fallback={<div className="skeleton h-[420px] rounded-card" />}>
        <Form />
      </Suspense>
    </>
  );
}

async function Form() {
  const [movies, halls] = await Promise.all([listMovies(), listHalls()]);
  return (
    <ShowtimeForm
      showtime={{ movieId: "", hallId: "", startTime: "", endTime: "", standardPrice: "14.50", vipPrice: "22.00" }}
      zone={cinemaZone()}
      movies={movies.map((movie) => ({ id: movie.id, label: movie.title, durationMinutes: movie.durationMinutes }))}
      halls={halls.map((hall) => ({ id: hall.id, label: `${hall.name} (${hall.seatCount} seats)` }))}
    />
  );
}
