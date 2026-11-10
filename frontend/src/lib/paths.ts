// Addresses inside this site. Pure functions: usable on the server, in the browser and in tests.

/** Where to send somebody who has to log in first, so they come back to where they were. */
export function loginPath(next: string): string {
  return `/login?next=${encodeURIComponent(next)}`;
}

/**
 * Only paths inside this site are accepted as a place to return to after
 * logging in; anything else (another site, a protocol-relative URL) is ignored.
 */
export function safeNextPath(next: string | undefined | null): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.includes("\\")) {
    return "/";
  }
  return next;
}
