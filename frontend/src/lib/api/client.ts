import "server-only";

import type { Problem } from "./types";

/**
 * Every call to the booking API goes through here, and only from the server:
 * the browser talks to this application, never to the API, so the access
 * token never has to be readable by scripts in the page.
 */

/** Where the API is, as seen from this server (inside docker compose: http://api:8080). */
export function apiBaseUrl(): string {
  return (process.env.API_URL ?? "http://localhost:8080").replace(/\/+$/, "");
}

/** A failed call: the HTTP status and the problem the API described. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly problem: Problem;

  constructor(status: number, problem: Problem) {
    super(problem.detail ?? problem.title ?? `The API answered ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.code = problem.code ?? "UNKNOWN";
    this.problem = problem;
  }
}

/** Raised when no answer came back at all: the API is down or unreachable. */
export const API_UNREACHABLE = "API_UNREACHABLE";

export type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  /** Sent as JSON. */
  body?: unknown;
  /** The caller's access token, when the call is made on behalf of a logged-in user. */
  token?: string;
  query?: Record<string, string | number | undefined>;
  headers?: Record<string, string | undefined>;
  signal?: AbortSignal;
};

export function apiUrl(path: string, query?: RequestOptions["query"]): string {
  const url = new URL(apiBaseUrl() + "/api/v1" + path);
  for (const [name, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== "") {
      url.searchParams.set(name, String(value));
    }
  }
  return url.toString();
}

/**
 * Calls the API and returns the parsed body (undefined for 204).
 * Throws {@link ApiError} for every answer that is not a success.
 */
export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers({ Accept: "application/json" });
  if (options.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  if (options.token) {
    headers.set("Authorization", `Bearer ${options.token}`);
  }
  for (const [name, value] of Object.entries(options.headers ?? {})) {
    if (value) {
      headers.set(name, value);
    }
  }

  let response: Response;
  try {
    response = await fetch(apiUrl(path, options.query), {
      method: options.method ?? "GET",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
      // Seats, holds and bookings change by the second: never serve a stored answer.
      cache: "no-store",
    });
  } catch (cause) {
    if (cause instanceof Error && cause.name === "AbortError") {
      throw cause;
    }
    throw new ApiError(503, {
      code: API_UNREACHABLE,
      title: "Service Unavailable",
      detail: "The booking service is not answering",
    });
  }

  if (response.status === 204) {
    return undefined as T;
  }
  const body = await readJson(response);
  if (!response.ok) {
    const problem = (body ?? {}) as Problem;
    throw new ApiError(response.status, {
      ...problem,
      // The header is on every response, also on errors that carry no body.
      traceId: problem.traceId ?? response.headers.get("X-Trace-Id") ?? undefined,
    });
  }
  return body as T;
}

async function readJson(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) {
    return undefined;
  }
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}
