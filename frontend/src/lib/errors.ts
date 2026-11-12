import type { Problem } from "./api/types";

/**
 * What a failed action hands back to the page. Plain data, because it crosses
 * from the server to the browser.
 */
export type ActionError = {
  /** The API's stable error code, or one of this application's own. */
  code: string;
  /** A sentence for the customer. */
  message: string;
  /** The trace of the failed request, shown as a reference to quote. */
  traceId?: string;
  /** Messages per form field, from a VALIDATION_FAILED response. */
  fieldErrors?: Record<string, string>;
  /** The seats that were lost to somebody else. */
  seatIds?: number[];
  /** The hold the customer already has for this showtime. */
  holdId?: string;
};

export type ActionResult<T = undefined> = { ok: true; data: T } | { ok: false; error: ActionError };

export const success = <T>(data: T): ActionResult<T> => ({ ok: true, data });

/** The words for each error code. Codes not listed fall back to the API's own sentence. */
const MESSAGES: Record<string, string> = {
  API_UNREACHABLE: "The booking service is not answering. Try again in a moment.",
  SERVICE_UNAVAILABLE: "The booking service is having trouble. Try again in a moment.",
  INTERNAL_ERROR: "Something went wrong on our side. Try again in a moment.",
  UNAUTHENTICATED: "Your session has ended. Log in again to continue.",
  FORBIDDEN: "Your account is not allowed to do this.",
  INVALID_CREDENTIALS: "The email or the password is not right.",
  EMAIL_TAKEN: "An account with this email already exists. Log in instead.",
  RESET_LINK_INVALID: "This link has expired or has already been used.",
  GOOGLE_SIGN_IN_FAILED: "Signing in with Google did not work. Try again.",
  GOOGLE_EMAIL_UNVERIFIED: "Google has not confirmed this email address yet. Confirm it with Google and try again.",
  PASSWORD_INCORRECT: "That is not the password of this account.",
  ACCOUNT_NOT_DELETABLE: "An administrator account cannot be deleted.",
  TOO_MANY_REQUESTS: "Too many attempts. Wait a minute and try again.",
  VALIDATION_FAILED: "Some fields need another look.",
  SEAT_ALREADY_HELD: "Somebody else is booking some of these seats right now. Pick other seats.",
  SEAT_ALREADY_BOOKED: "Some of these seats have just been sold. Pick other seats.",
  TOO_MANY_SEATS: "You can hold at most 8 seats at once.",
  HOLD_ALREADY_EXISTS: "You already have seats on hold for this showtime.",
  HOLD_NOT_FOUND: "Your hold has run out and the seats are back on sale. Pick your seats again.",
  SHOWTIME_ALREADY_STARTED: "This showtime has already started.",
  SHOWTIME_NOT_FOUND: "This showtime is no longer on the programme.",
  PAYMENT_IN_PROGRESS: "Your payment is still being processed. Give it a moment.",
  PAYMENT_REFUNDED: "The booking could not be completed and your payment was refunded. Pick your seats again.",
  PAYMENT_PROVIDER_UNAVAILABLE: "The payment provider is not answering. You were not charged. Try again.",
  CONCURRENT_UPDATE: "This booking was changed at the same moment. Try again.",
  MOVIE_HAS_SHOWTIMES: "This movie still has showtimes. Remove them first.",
  HALL_HAS_SHOWTIMES: "This hall still has showtimes. Remove them first.",
  SHOWTIME_HAS_BOOKINGS: "Tickets have been sold for this showtime, so it cannot be removed or moved to another hall.",
};

/**
 * The sentence for a sign-in with Google that ended back on the login form.
 * The reason arrives in the address, so it is looked up, never shown as it is:
 * an address anybody can write must not put words onto the page.
 */
export function googleProblem(code: string): string | undefined {
  const known = ["GOOGLE_SIGN_IN_FAILED", "GOOGLE_EMAIL_UNVERIFIED", "TOO_MANY_REQUESTS"];
  if (code === "EMAIL_TAKEN") {
    return "The account with this email is connected to another Google account.";
  }
  return known.includes(code) ? MESSAGES[code] : undefined;
}

const DECLINES: Record<string, string> = {
  insufficient_funds: "The card was declined: insufficient funds. Your seats are still held. Try another card.",
  expired_card: "The card was declined: it has expired. Your seats are still held. Try another card.",
};

/** Turns the API's description of a failure into what the page shows. */
export function describeProblem(problem: Problem, status = problem.status ?? 500): ActionError {
  const code = problem.code ?? "UNKNOWN";
  let message = MESSAGES[code] ?? problem.detail ?? "Something went wrong. Try again.";
  if (code === "PAYMENT_DECLINED") {
    message =
      DECLINES[problem.declineCode ?? ""] ?? "The card was declined. Your seats are still held. Try another card.";
  }
  if (status >= 500 && !MESSAGES[code]) {
    message = MESSAGES.INTERNAL_ERROR;
  }

  const error: ActionError = { code, message };
  if (problem.traceId) {
    error.traceId = problem.traceId;
  }
  if (problem.errors?.length) {
    error.fieldErrors = {};
    for (const { field, message: fieldMessage } of problem.errors) {
      // Keep the first message per field; "prices[VIP]" style names are kept as they are.
      error.fieldErrors[field] ??= capitalize(fieldMessage);
    }
  }
  if (problem.seatIds?.length) {
    error.seatIds = problem.seatIds;
  }
  if (problem.holdId) {
    error.holdId = problem.holdId;
  }
  return error;
}

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}
