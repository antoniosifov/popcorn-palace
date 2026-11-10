import "server-only";

import { isValidZone } from "./time";

/**
 * Settings read from the environment of the server, at request time, so one
 * image runs anywhere. Nothing here is compiled into the browser bundle.
 */

/** The time zone of the cinema: every showtime is shown on its clock. */
export function cinemaZone(): string {
  const zone = process.env.CINEMA_TIME_ZONE;
  return zone && isValidZone(zone) ? zone : "UTC";
}

/** ISO 4217 code of the currency all prices are in (the API's PAYMENT_CURRENCY). */
export function currency(): string {
  return process.env.CURRENCY ?? "USD";
}

/**
 * Whether the session cookie is sent over HTTPS only. On by default in a
 * production build; switch it off only where the site is served over plain
 * HTTP on purpose, as the local docker compose stack is.
 */
export function secureCookies(): boolean {
  const setting = process.env.SESSION_COOKIE_SECURE;
  if (setting !== undefined && setting !== "") {
    return setting !== "false";
  }
  return process.env.NODE_ENV === "production";
}
