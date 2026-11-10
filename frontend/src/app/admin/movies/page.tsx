import Link from "next/link";
import { Suspense } from "react";

import { removeMovie } from "@/actions/admin";
import { ConfirmButton } from "@/components/confirm-button";
import { Empty, TitleRow } from "@/components/ui";
import { listMovies } from "@/lib/api/catalog";
import { formatDuration } from "@/lib/time";

export default function AdminMoviesPage() {
  return (
    <>
      <TitleRow
        title="Movies"
        sub="The catalog. A movie with showtimes cannot be deleted."
        action={
          <Link href="/admin/movies/new" className="btn btn-primary">
            Add a movie
          </Link>
        }
      />
      <section className="card">
        <Suspense fallback={<div className="skeleton h-[280px]" />}>
          <Movies />
        </Suspense>
      </section>
    </>
  );
}

async function Movies() {
  const movies = await listMovies();
  if (movies.length === 0) {
    return <Empty>The catalog is empty. Add the first movie.</Empty>;
  }
  return (
    <div className="overflow-x-auto">
      <p className="mb-2 text-label text-muted">
        <span className="num">{movies.length}</span> {movies.length === 1 ? "movie" : "movies"}
      </p>
      <table className="table min-w-[680px]">
        <thead>
          <tr>
            <th scope="col">Title</th>
            <th scope="col">Genre</th>
            <th scope="col" className="n">
              Length
            </th>
            <th scope="col" className="n">
              Rating
            </th>
            <th scope="col" className="n">
              Year
            </th>
            <th scope="col" className="n">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {movies.map((movie) => (
            <tr key={movie.id}>
              <td className="font-medium">
                <Link href={`/movies/${movie.id}`} className="hover:underline hover:underline-offset-4">
                  {movie.title}
                </Link>
              </td>
              <td className="text-muted">{movie.genre}</td>
              <td className="n">{formatDuration(movie.durationMinutes)}</td>
              <td className="n">{movie.rating.toFixed(1)}</td>
              <td className="n">{movie.releaseYear}</td>
              <td className="n whitespace-nowrap">
                <Link href={`/admin/movies/${movie.id}`} className="link mr-4">
                  Edit
                </Link>
                <ConfirmButton
                  look="text"
                  label="Delete"
                  question={`Delete ${movie.title}?`}
                  confirmLabel="Delete movie"
                  action={removeMovie.bind(null, movie.id)}
                />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
