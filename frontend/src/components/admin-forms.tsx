"use client";

import Link from "next/link";
import { startTransition, useActionState, useState, type FormEvent } from "react";

import { saveHall, saveMovie, saveShowtime, type AdminFormState } from "@/actions/admin";
import type { SeatCategory } from "@/lib/api/types";

import { HallPreview } from "./hall-preview";
import { ErrorNotice, Field, fieldProps } from "./ui";

/**
 * Sends a form to its Server Action and keeps what was typed when saving is
 * refused. A form given straight to `action` is emptied by React once the
 * action has run, which is right after a success and wrong after "this slot
 * is taken": everything would have to be filled in again. So the submit is
 * handled here and the fields are left alone.
 */
function useSave(save: (state: AdminFormState, form: FormData) => Promise<AdminFormState>) {
  const [state, action, pending] = useActionState<AdminFormState, FormData>(save, {});
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    startTransition(() => action(form));
  };
  return { error: state.error, errors: state.error?.fieldErrors ?? {}, pending, submit };
}

/** A form section: what it is about on the left, its fields in a panel on the right. */
function Section({ title, description, children }: { title: string; description: string; children: React.ReactNode }) {
  return (
    <div className="grid gap-x-10 gap-y-4 border-t border-rule py-8 first:border-t-0 first:pt-0 md:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
      <div className="pt-1.5">
        <h2 className="text-section font-medium">{title}</h2>
        <p className="mt-1 text-muted">{description}</p>
      </div>
      <div className="card grid gap-4 rounded-[16px] p-4">{children}</div>
    </div>
  );
}

function Actions({ pending, label, cancelHref }: { pending: boolean; label: string; cancelHref: string }) {
  return (
    <div className="flex justify-end gap-2 border-t border-rule pt-6">
      <Link href={cancelHref} className="btn btn-secondary">
        Cancel
      </Link>
      <button type="submit" className="btn btn-primary" disabled={pending}>
        {pending ? "Saving…" : label}
      </button>
    </div>
  );
}

// ---------------------------------------------------------------- movie

export type MovieValues = {
  id?: number;
  title: string;
  genre: string;
  durationMinutes: string;
  rating: string;
  releaseYear: string;
  overview?: string;
  tagline?: string;
  posterUrl?: string;
  backdropUrl?: string;
  trailerUrl?: string;
  director?: string;
  /** The cast as it is typed: names separated by commas. */
  starring?: string;
};

export function MovieForm({ movie }: { movie?: MovieValues }) {
  const { error, errors, pending, submit } = useSave(saveMovie);
  const value = (name: keyof MovieValues) => String(movie?.[name] ?? "");

  return (
    <form onSubmit={submit}>
      {movie?.id ? <input type="hidden" name="id" value={movie.id} /> : null}
      <div className="mb-6">
        <ErrorNotice error={error} />
      </div>
      <Section title="The movie" description="As it appears on the programme and on tickets.">
        <Field label="Title" name="title" error={errors.title}>
          <input
            {...fieldProps("title", errors.title)}
            className="input"
            maxLength={200}
            required
            defaultValue={value("title")}
          />
        </Field>
        <Field label="Genre" name="genre" error={errors.genre}>
          <input
            {...fieldProps("genre", errors.genre)}
            className="input"
            maxLength={100}
            required
            defaultValue={value("genre")}
          />
        </Field>
      </Section>
      <Section title="The facts" description="The length decides when a showtime of this movie ends.">
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Length, minutes" name="durationMinutes" error={errors.durationMinutes}>
            <input
              {...fieldProps("durationMinutes", errors.durationMinutes)}
              className="input num"
              type="number"
              min={1}
              max={900}
              required
              defaultValue={value("durationMinutes")}
            />
          </Field>
          <Field label="Rating, 0 to 10" name="rating" error={errors.rating}>
            <input
              {...fieldProps("rating", errors.rating)}
              className="input num"
              type="number"
              min={0}
              max={10}
              step={0.1}
              required
              defaultValue={value("rating")}
            />
          </Field>
          <Field label="Year" name="releaseYear" error={errors.releaseYear}>
            <input
              {...fieldProps("releaseYear", errors.releaseYear)}
              className="input num"
              type="number"
              min={1888}
              max={2100}
              required
              defaultValue={value("releaseYear")}
            />
          </Field>
        </div>
      </Section>
      <Section
        title="On the programme"
        description="What a customer reads before choosing. All of it is optional: a film without a poster gets a drawn title card."
      >
        <Field label="What it is about" name="overview" error={errors.overview}>
          <textarea
            {...fieldProps("overview", errors.overview)}
            className="input h-36 resize-y py-3 leading-6"
            maxLength={4000}
            defaultValue={value("overview")}
          />
        </Field>
        <Field label="Tagline" name="tagline" error={errors.tagline}>
          <input
            {...fieldProps("tagline", errors.tagline)}
            className="input"
            maxLength={300}
            defaultValue={value("tagline")}
          />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Directed by" name="director" error={errors.director}>
            <input
              {...fieldProps("director", errors.director)}
              className="input"
              maxLength={200}
              defaultValue={value("director")}
            />
          </Field>
          <Field label="Starring" name="starring" error={errors.starring} hint="Names separated by commas.">
            <input
              {...fieldProps("starring", errors.starring, "Names separated by commas.")}
              className="input"
              defaultValue={value("starring")}
            />
          </Field>
        </div>
      </Section>
      <Section
        title="Artwork"
        description="Addresses of pictures that are already on the web, starting with https. Films imported from TMDB come with theirs."
      >
        <Field
          label="Poster"
          name="posterUrl"
          error={errors.posterUrl}
          hint="An upright picture, two wide by three tall."
        >
          <input
            {...fieldProps("posterUrl", errors.posterUrl, "An upright picture, two wide by three tall.")}
            className="input"
            type="url"
            inputMode="url"
            maxLength={500}
            defaultValue={value("posterUrl")}
          />
        </Field>
        <Field
          label="Still"
          name="backdropUrl"
          error={errors.backdropUrl}
          hint="A wide frame of the film; it fills the top of its page."
        >
          <input
            {...fieldProps(
              "backdropUrl",
              errors.backdropUrl,
              "A wide frame of the film; it fills the top of its page.",
            )}
            className="input"
            type="url"
            inputMode="url"
            maxLength={500}
            defaultValue={value("backdropUrl")}
          />
        </Field>
        <Field label="Trailer" name="trailerUrl" error={errors.trailerUrl}>
          <input
            {...fieldProps("trailerUrl", errors.trailerUrl)}
            className="input"
            type="url"
            inputMode="url"
            maxLength={500}
            defaultValue={value("trailerUrl")}
          />
        </Field>
      </Section>
      <Actions pending={pending} label={movie?.id ? "Save movie" : "Add movie"} cancelHref="/admin/movies" />
    </form>
  );
}

// ----------------------------------------------------------------- hall

type Block = { key: number; rows: string; seatsPerRow: string; category: SeatCategory };

export function HallForm() {
  const { error, errors, pending, submit } = useSave(saveHall);
  const [blocks, setBlocks] = useState<Block[]>([
    { key: 1, rows: "8", seatsPerRow: "12", category: "STANDARD" },
    { key: 2, rows: "2", seatsPerRow: "10", category: "VIP" },
  ]);
  const [nextKey, setNextKey] = useState(3);

  const update = (key: number, change: Partial<Block>) =>
    setBlocks((current) => current.map((block) => (block.key === key ? { ...block, ...change } : block)));

  // The layout as it would be built: rows numbered through the blocks, front to back.
  const preview: { row: number; category: SeatCategory; seats: number }[] = [];
  for (const block of blocks) {
    const rows = Math.min(60, Math.max(0, Math.floor(Number(block.rows)) || 0));
    const seats = Math.min(50, Math.max(0, Math.floor(Number(block.seatsPerRow)) || 0));
    for (let index = 0; index < rows && seats > 0; index++) {
      preview.push({ row: preview.length + 1, category: block.category, seats });
    }
  }
  const seatCount = preview.reduce((total, row) => total + row.seats, 0);

  return (
    <form onSubmit={submit}>
      <div className="mb-6">
        <ErrorNotice error={error} />
      </div>
      <Section title="The hall" description="Its name on the programme and on tickets.">
        <Field label="Name" name="name" error={errors.name}>
          <input {...fieldProps("name", errors.name)} className="input" maxLength={100} required />
        </Field>
      </Section>

      <Section
        title="The seats"
        description="Blocks of identical rows, from the screen to the back. A layout cannot be changed once the hall exists, because tickets point at its seats."
      >
        {blocks.map((block, index) => {
          const prefix = `sections[${index}]`;
          const rowsError = errors[`${prefix}.rows`];
          const seatsError = errors[`${prefix}.seatsPerRow`];
          return (
            <fieldset key={block.key} className="grid grid-cols-[1fr_1fr_1.2fr_auto] items-end gap-3">
              <legend className="sr-only">Block {index + 1}</legend>
              <Field label="Rows" name={`${prefix}.rows`} error={rowsError}>
                <input
                  {...fieldProps(`${prefix}.rows`, rowsError)}
                  className="input num"
                  type="number"
                  min={1}
                  max={50}
                  required
                  value={block.rows}
                  onChange={(event) => update(block.key, { rows: event.target.value })}
                />
              </Field>
              <Field label="Seats a row" name={`${prefix}.seatsPerRow`} error={seatsError}>
                <input
                  {...fieldProps(`${prefix}.seatsPerRow`, seatsError)}
                  className="input num"
                  type="number"
                  min={1}
                  max={50}
                  required
                  value={block.seatsPerRow}
                  onChange={(event) => update(block.key, { seatsPerRow: event.target.value })}
                />
              </Field>
              <Field label="Category" name={`${prefix}.category`}>
                <select
                  {...fieldProps(`${prefix}.category`)}
                  className="input"
                  value={block.category}
                  onChange={(event) => update(block.key, { category: event.target.value as SeatCategory })}
                >
                  <option value="STANDARD">Standard</option>
                  <option value="VIP">VIP</option>
                </select>
              </Field>
              <button
                type="button"
                className="btn btn-secondary h-10"
                disabled={blocks.length === 1}
                aria-label={`Remove block ${index + 1}`}
                onClick={() => setBlocks((current) => current.filter((candidate) => candidate.key !== block.key))}
              >
                Remove
              </button>
            </fieldset>
          );
        })}
        <div>
          <button
            type="button"
            className="btn btn-secondary"
            disabled={blocks.length >= 20}
            onClick={() => {
              setBlocks((current) => [
                ...current,
                { key: nextKey, rows: "1", seatsPerRow: "10", category: "STANDARD" },
              ]);
              setNextKey((key) => key + 1);
            }}
          >
            Add a block
          </button>
        </div>
      </Section>

      <Section
        title="What it makes"
        description={`${preview.length} ${preview.length === 1 ? "row" : "rows"}, ${seatCount} ${seatCount === 1 ? "seat" : "seats"}. A hall holds at most 60 rows and 1,000 seats.`}
      >
        <HallPreview rows={preview} />
      </Section>
      <Actions pending={pending} label="Create hall" cancelHref="/admin/halls" />
    </form>
  );
}

// ------------------------------------------------------------- showtime

export type ShowtimeValues = {
  id?: number;
  movieId: string;
  hallId: string;
  /** Wall-clock times on the cinema's clock, as a datetime-local input holds them. */
  startTime: string;
  endTime: string;
  standardPrice: string;
  vipPrice: string;
};

type Option = { id: number; label: string };

/** Adds minutes to a wall-clock date and time ("2030-01-15T18:00"), staying on the wall clock. */
function addMinutes(local: string, minutes: number): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(local);
  if (!match) {
    return "";
  }
  const [year, month, day, hour, minute] = match.slice(1).map(Number);
  return new Date(Date.UTC(year, month - 1, day, hour, minute + minutes)).toISOString().slice(0, 16);
}

export function ShowtimeForm({
  showtime,
  movies,
  halls,
  zone,
}: {
  showtime: ShowtimeValues;
  movies: (Option & { durationMinutes: number })[];
  halls: Option[];
  zone: string;
}) {
  const { error, errors, pending, submit } = useSave(saveShowtime);

  const [movieId, setMovieId] = useState(showtime.movieId);
  const [start, setStart] = useState(showtime.startTime);
  const [end, setEnd] = useState(showtime.endTime);
  /** Until somebody sets the end by hand, it follows the start and the length of the movie. */
  const [endIsOwn, setEndIsOwn] = useState(Boolean(showtime.id));

  const duration = movies.find((movie) => String(movie.id) === movieId)?.durationMinutes;
  const follow = (nextMovieId: string, nextStart: string) => {
    const minutes = movies.find((movie) => String(movie.id) === nextMovieId)?.durationMinutes;
    if (!endIsOwn && minutes && nextStart) {
      setEnd(addMinutes(nextStart, minutes));
    }
  };

  const timeHint = `On the cinema's clock (${zone}).`;
  return (
    <form onSubmit={submit}>
      {showtime.id ? <input type="hidden" name="id" value={showtime.id} /> : null}
      <div className="mb-6">
        <ErrorNotice error={error} />
      </div>

      <Section title="What and where" description="Two showtimes cannot overlap in the same hall.">
        <Field label="Movie" name="movieId" error={errors.movieId}>
          <select
            {...fieldProps("movieId", errors.movieId)}
            className="input"
            required
            value={movieId}
            onChange={(event) => {
              setMovieId(event.target.value);
              follow(event.target.value, start);
            }}
          >
            <option value="">Choose a movie</option>
            {movies.map((movie) => (
              <option key={movie.id} value={movie.id}>
                {movie.label}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Hall" name="hallId" error={errors.hallId}>
          <select {...fieldProps("hallId", errors.hallId)} className="input" required defaultValue={showtime.hallId}>
            <option value="">Choose a hall</option>
            {halls.map((hall) => (
              <option key={hall.id} value={hall.id}>
                {hall.label}
              </option>
            ))}
          </select>
        </Field>
      </Section>

      <Section
        title="When"
        description={
          duration
            ? `The movie runs ${duration} minutes, so the end is set that long after the start. Move it later to leave time between showtimes.`
            : "Choose a movie and a start; the end follows from the length of the movie."
        }
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Starts" name="startTime" error={errors.startTime} hint={timeHint}>
            <input
              {...fieldProps("startTime", errors.startTime, timeHint)}
              className="input num"
              type="datetime-local"
              required
              value={start}
              onChange={(event) => {
                setStart(event.target.value);
                follow(movieId, event.target.value);
              }}
            />
          </Field>
          <Field label="Ends" name="endTime" error={errors.endTime} hint="After the start.">
            <input
              {...fieldProps("endTime", errors.endTime, "After the start.")}
              className="input num"
              type="datetime-local"
              required
              value={end}
              onChange={(event) => {
                setEnd(event.target.value);
                setEndIsOwn(true);
              }}
            />
          </Field>
        </div>
      </Section>

      <Section
        title="Prices"
        description="One per seat category. A hall without VIP seats needs only the standard price."
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Standard" name="prices[STANDARD]" error={errors["prices[STANDARD]"]}>
            <input
              {...fieldProps("prices[STANDARD]", errors["prices[STANDARD]"])}
              className="input num"
              type="number"
              min={0}
              step={0.01}
              defaultValue={showtime.standardPrice}
            />
          </Field>
          <Field label="VIP" name="prices[VIP]" error={errors["prices[VIP]"]}>
            <input
              {...fieldProps("prices[VIP]", errors["prices[VIP]"])}
              className="input num"
              type="number"
              min={0}
              step={0.01}
              defaultValue={showtime.vipPrice}
            />
          </Field>
        </div>
      </Section>
      <Actions
        pending={pending}
        label={showtime.id ? "Save showtime" : "Schedule showtime"}
        cancelHref="/admin/showtimes"
      />
    </form>
  );
}
