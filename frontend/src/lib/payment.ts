/**
 * The payment provider is a sandbox: no card number is ever typed or sent.
 * A token stands in for a card and decides how the payment goes, which is
 * what makes every outcome of a checkout possible to try.
 */
export type PaymentMethod = {
  token: string;
  label: string;
  /** What paying with it does. */
  outcome: string;
};

export const PAYMENT_METHODS: PaymentMethod[] = [
  { token: "tok_visa", label: "Test card 1", outcome: "Approved" },
  { token: "tok_slow", label: "Test card 2", outcome: "Approved after a delay" },
  { token: "tok_declined", label: "Test card 3", outcome: "Declined: insufficient funds" },
  { token: "tok_expired", label: "Test card 4", outcome: "Declined: expired card" },
  { token: "tok_error", label: "Test card 5", outcome: "The payment provider fails" },
];

/**
 * Whether an answer to a payment settles the attempt. When it does, the next
 * attempt needs a new idempotency key, because the API replays the recorded
 * answer for a used key. When it does not (no answer at all, or "still being
 * processed"), the same key must be sent again, so that the attempt can never
 * be charged twice.
 */
export function settlesAttempt(errorCode: string): boolean {
  return errorCode !== "PAYMENT_IN_PROGRESS" && errorCode !== "API_UNREACHABLE" && errorCode !== "SERVICE_UNAVAILABLE";
}

/** A new key for a new purchase attempt. */
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}
