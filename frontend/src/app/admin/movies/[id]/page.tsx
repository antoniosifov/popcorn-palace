import { notFound } from "next/navigation";
import { Suspense } from "react";

import { MovieForm } from "@/components/admin-forms";
import { TitleRow } from "@/components/ui";
import { getMovie } from "@/lib/api/catalog";
import { ApiError } from "@/lib/api/client";
import type { Movie } from "@/lib/api/types";

export default function EditMoviePage({ params }: PageProps<"/admin/movies/[id]">) {
  return (
    <>
      <TitleRow title="Edit movie" />
      <Suspense fallback={<div className="skeleton h-[360px] rounded-card" />}>
        <Form params={params} />
      </Suspense>
    </>
  );
}

async function Form({ params }: Pick<PageProps<"/admin/movies/[id]">, "params">) {
  const { id } = await params;
  const movieId = Number(id);
  if (!Number.isSafeInteger(movieId) || movieId <= 0) {
    notFound();
  }
  let movie: Movie;
  try {
    movie = await getMovie(movieId);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
    throw error;
  }
  return (
    <MovieForm
      movie={{
        id: movie.id,
        title: movie.title,
        genre: movie.genre,
        durationMinutes: String(movie.durationMinutes),
        rating: movie.rating.toFixed(1),
        releaseYear: String(movie.releaseYear),
      }}
    />
  );
}
