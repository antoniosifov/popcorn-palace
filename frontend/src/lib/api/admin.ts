import "server-only";

import { api } from "./client";
import type { Draft, Hall, HallInput, Movie, MovieInput, Showtime, ShowtimeInput } from "./types";

// Managing the catalog. The API allows these to administrators only.

export const createMovie = (input: Draft<MovieInput>, token: string) =>
  api<Movie>("/movies", { method: "POST", body: input, token });

export const updateMovie = (id: number, input: Draft<MovieInput>, token: string) =>
  api<Movie>(`/movies/${id}`, { method: "PUT", body: input, token });

export const deleteMovie = (id: number, token: string) => api<void>(`/movies/${id}`, { method: "DELETE", token });

export const createHall = (input: HallInput, token: string) =>
  api<Hall>("/halls", { method: "POST", body: input, token });

export const deleteHall = (id: number, token: string) => api<void>(`/halls/${id}`, { method: "DELETE", token });

export const createShowtime = (input: Draft<ShowtimeInput>, token: string) =>
  api<Showtime>("/showtimes", { method: "POST", body: input, token });

export const updateShowtime = (id: number, input: Draft<ShowtimeInput>, token: string) =>
  api<Showtime>(`/showtimes/${id}`, { method: "PUT", body: input, token });

export const deleteShowtime = (id: number, token: string) => api<void>(`/showtimes/${id}`, { method: "DELETE", token });
