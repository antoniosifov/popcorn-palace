import "server-only";

import { api } from "./client";
import type { Hall, HallSummary, Movie, Page, SeatMap, Showtime } from "./types";

/** The API returns at most this many items per page. */
const PAGE_SIZE = 100;
/** A stop for lists that are read to the end; far more than a cinema has. */
const MAX_PAGES = 20;

/** Reads a paginated list to its end. */
async function all<T>(path: string, query: Record<string, string | number | undefined>): Promise<T[]> {
  const items: T[] = [];
  for (let page = 0; page < MAX_PAGES; page++) {
    const result = await api<Page<T>>(path, { query: { ...query, page, size: PAGE_SIZE } });
    items.push(...result.content);
    if (page + 1 >= result.totalPages) {
      break;
    }
  }
  return items;
}

export function listMovies(): Promise<Movie[]> {
  return all<Movie>("/movies", { sort: "title,asc" });
}

export function getMovie(id: number): Promise<Movie> {
  return api<Movie>(`/movies/${id}`);
}

export function listHalls(): Promise<HallSummary[]> {
  return all<HallSummary>("/halls", { sort: "name,asc" });
}

export function getHall(id: number): Promise<Hall> {
  return api<Hall>(`/halls/${id}`);
}

/** Showtimes that start inside the window, earliest first. */
export function listShowtimes(filter: { from?: Date; to?: Date; movieId?: number } = {}): Promise<Showtime[]> {
  return all<Showtime>("/showtimes", {
    sort: "startTime,asc",
    from: filter.from?.toISOString(),
    to: filter.to?.toISOString(),
    movieId: filter.movieId,
  });
}

export function getShowtime(id: number): Promise<Showtime> {
  return api<Showtime>(`/showtimes/${id}`);
}

/** The seat map; with a token, the caller's own held seats are marked. */
export function getSeatMap(showtimeId: number, token?: string): Promise<SeatMap> {
  return api<SeatMap>(`/showtimes/${showtimeId}/seats`, { token });
}
