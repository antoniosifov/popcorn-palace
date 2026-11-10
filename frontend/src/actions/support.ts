import "server-only";

import { ApiError } from "@/lib/api/client";
import { describeProblem, type ActionError, type ActionResult } from "@/lib/errors";
import { endSession, sessionToken } from "@/lib/session";

/**
 * Server Actions are plain POST endpoints: anybody can call them with any
 * arguments, whatever the page offers. So every action checks its input and
 * its caller itself, and the API checks both again.
 */

export const UNAUTHENTICATED: ActionError = {
  code: "UNAUTHENTICATED",
  message: "Your session has ended. Log in again to continue.",
};

export const INVALID_INPUT: ActionError = {
  code: "INVALID_INPUT",
  message: "That request did not make sense. Reload the page and try again.",
};

export const failure = (error: ActionError): { ok: false; error: ActionError } => ({ ok: false, error });

/**
 * Turns a failed API call into what the page shows. Anything that is not an
 * answer from the API is a bug and is thrown on, to the error boundary.
 */
export async function describeFailure(error: unknown): Promise<ActionError> {
  if (!(error instanceof ApiError)) {
    throw error;
  }
  if (error.status === 401 && error.code === "UNAUTHENTICATED") {
    // The token ran out or was rejected: forget it, so the page shows the visitor as logged out.
    await endSession();
  }
  return describeProblem(error.problem, error.status);
}

/** Runs an API call on behalf of the logged-in user and reports the outcome as data. */
export async function asUser<T>(call: (token: string) => Promise<T>): Promise<ActionResult<T>> {
  const token = await sessionToken();
  if (!token) {
    return failure(UNAUTHENTICATED);
  }
  try {
    return { ok: true, data: await call(token) };
  } catch (error) {
    return failure(await describeFailure(error));
  }
}

export const isPositiveInteger = (value: unknown): value is number =>
  typeof value === "number" && Number.isSafeInteger(value) && value > 0;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const isUuid = (value: unknown): value is string => typeof value === "string" && UUID.test(value);

export function text(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value.trim() : "";
}
