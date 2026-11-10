import "server-only";

import { connection } from "next/server";

/**
 * The moment of this request. Waiting for the connection first marks the value
 * as belonging to a request, so it can never be computed ahead of time and
 * frozen into a prerendered page.
 */
export async function requestTime(): Promise<Date> {
  await connection();
  return new Date();
}
