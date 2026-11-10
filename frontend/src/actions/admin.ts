"use server";

import { refresh } from "next/cache";
import { redirect } from "next/navigation";

import * as admin from "@/lib/api/admin";
import type { Draft, HallSection, Prices, SeatCategory } from "@/lib/api/types";
import { cinemaZone } from "@/lib/config";
import type { ActionError, ActionResult } from "@/lib/errors";
import { fromLocalDateTime } from "@/lib/time";

import { asUser, failure, INVALID_INPUT, isPositiveInteger, text } from "./support";

/** What an admin form gets back when saving was refused. The form itself keeps what was typed. */
export type AdminFormState = {
  error?: ActionError;
};

/** An empty field is sent as null, so the API answers "must not be null" instead of this code guessing. */
function numberOrNull(value: string): number | null {
  if (value.trim() === "") {
    return null;
  }
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function idOf(form: FormData): number | null {
  const id = Number(text(form, "id"));
  return isPositiveInteger(id) ? id : null;
}

// ---------------------------------------------------------------- movies

export async function saveMovie(_previous: AdminFormState, form: FormData): Promise<AdminFormState> {
  const id = idOf(form);
  const input = {
    title: text(form, "title"),
    genre: text(form, "genre"),
    durationMinutes: numberOrNull(text(form, "durationMinutes")),
    rating: numberOrNull(text(form, "rating")),
    releaseYear: numberOrNull(text(form, "releaseYear")),
  };
  // The API validates every field and names the ones it rejects.
  const result = await asUser((token) => (id ? admin.updateMovie(id, input, token) : admin.createMovie(input, token)));
  if (!result.ok) {
    return { error: result.error };
  }
  redirect("/admin/movies");
}

export async function removeMovie(id: number): Promise<ActionResult<undefined>> {
  return remove(id, admin.deleteMovie);
}

// ----------------------------------------------------------------- halls

const CATEGORIES: SeatCategory[] = ["STANDARD", "VIP"];

/** The blocks of rows, from the fields named sections[0].rows, sections[0].seatsPerRow, sections[0].category ... */
function sections(form: FormData): Draft<HallSection>[] {
  const result: Draft<HallSection>[] = [];
  for (let index = 0; index < 20; index++) {
    const prefix = `sections[${index}]`;
    if (!form.has(`${prefix}.rows`)) {
      break;
    }
    const category = text(form, `${prefix}.category`) as SeatCategory;
    result.push({
      rows: numberOrNull(text(form, `${prefix}.rows`)),
      seatsPerRow: numberOrNull(text(form, `${prefix}.seatsPerRow`)),
      category: CATEGORIES.includes(category) ? category : "STANDARD",
    });
  }
  return result;
}

export async function saveHall(_previous: AdminFormState, form: FormData): Promise<AdminFormState> {
  const input = { name: text(form, "name"), sections: sections(form) };
  const result = await asUser((token) => admin.createHall(input, token));
  if (!result.ok) {
    return { error: result.error };
  }
  redirect("/admin/halls");
}

export async function removeHall(id: number): Promise<ActionResult<undefined>> {
  return remove(id, admin.deleteHall);
}

// ------------------------------------------------------------- showtimes

export async function saveShowtime(_previous: AdminFormState, form: FormData): Promise<AdminFormState> {
  const id = idOf(form);
  const zone = cinemaZone();
  // The form holds times on the cinema's clock; the API takes instants.
  const start = fromLocalDateTime(text(form, "startTime"), zone);
  const end = fromLocalDateTime(text(form, "endTime"), zone);
  if (!start || !end) {
    const fieldErrors: Record<string, string> = {};
    if (!start) {
      fieldErrors.startTime = "Enter a date and a time";
    }
    if (!end) {
      fieldErrors.endTime = "Enter a date and a time";
    }
    return { error: { code: "VALIDATION_FAILED", message: "Some fields need another look.", fieldErrors } };
  }

  const prices: Prices = {};
  for (const category of CATEGORIES) {
    const price = numberOrNull(text(form, `prices[${category}]`));
    if (price !== null) {
      prices[category] = price;
    }
  }
  const input = {
    movieId: numberOrNull(text(form, "movieId")),
    hallId: numberOrNull(text(form, "hallId")),
    startTime: start.toISOString(),
    endTime: end.toISOString(),
    prices,
  };
  const result = await asUser((token) =>
    id ? admin.updateShowtime(id, input, token) : admin.createShowtime(input, token),
  );
  if (!result.ok) {
    return { error: result.error };
  }
  redirect("/admin/showtimes");
}

export async function removeShowtime(id: number): Promise<ActionResult<undefined>> {
  return remove(id, admin.deleteShowtime);
}

// ---------------------------------------------------------------- shared

async function remove(
  id: number,
  call: (id: number, token: string) => Promise<void>,
): Promise<ActionResult<undefined>> {
  if (!isPositiveInteger(id)) {
    return failure(INVALID_INPUT);
  }
  const result = await asUser(async (token) => {
    await call(id, token);
    return undefined;
  });
  if (result.ok) {
    // The row is gone: draw the list again.
    refresh();
  }
  return result;
}
