import { createHash, randomBytes } from "node:crypto";

import { safeNextPath } from "./paths";

/**
 * Signing in with Google, the part that happens between this server and
 * Google. The browser is sent to Google with a description of this sign-in
 * attempt; Google sends it back with a one-time code; this server exchanges
 * the code for an ID token, which the API then checks and turns into a session.
 *
 * Nothing here trusts the browser. Three values tie the way out to the way back:
 *
 * - `state` comes back in the address and must match the cookie: the answer
 *   belongs to a sign-in this browser started, not one somebody else started
 *   and tricked it into finishing;
 * - `verifier` never leaves this server except as its hash (PKCE): a code
 *   that leaks on the way back is useless without it;
 * - `nonce` is put into the ID token by Google and checked by the API: the
 *   token answers this attempt and no other.
 */

export type GoogleSettings = {
  clientId: string;
  clientSecret: string;
  /** Where the browser is sent to sign in, and where a code is exchanged. Google's own, unless a test says otherwise. */
  authUrl: string;
  tokenUrl: string;
};

/** Both the client id and its secret, or signing in with Google is off. */
export function googleSettings(env: Record<string, string | undefined> = process.env): GoogleSettings | null {
  const clientId = env.GOOGLE_CLIENT_ID?.trim();
  const clientSecret = env.GOOGLE_CLIENT_SECRET?.trim();
  if (!clientId || !clientSecret) {
    return null;
  }
  return {
    clientId,
    clientSecret,
    authUrl: env.GOOGLE_AUTH_URL?.trim() || "https://accounts.google.com/o/oauth2/v2/auth",
    tokenUrl: env.GOOGLE_TOKEN_URL?.trim() || "https://oauth2.googleapis.com/token",
  };
}

/** The cookie that remembers one attempt between leaving for Google and coming back. */
export const ATTEMPT_COOKIE = "pp_google";
/** Sent only to the two addresses that need it. */
export const ATTEMPT_COOKIE_PATH = "/api/auth/google";
/** Longer than anybody takes to pick an account; short enough that a forgotten tab is no opening. */
export const ATTEMPT_SECONDS = 600;

export type SignInAttempt = {
  state: string;
  nonce: string;
  verifier: string;
  /** Where the visitor was going, to send them there afterwards. A path inside this site. */
  next: string;
};

const secret = () => randomBytes(32).toString("base64url");

export function newAttempt(next: string | null | undefined): SignInAttempt {
  return { state: secret(), nonce: secret(), verifier: secret(), next: safeNextPath(next) };
}

/** What Google is given instead of the verifier (PKCE, method S256). */
export function challengeOf(verifier: string): string {
  return createHash("sha256").update(verifier).digest("base64url");
}

/** The address at this site that Google sends the browser back to. It has to be registered with Google, to the letter. */
export function callbackUrl(origin: URL): string {
  return new URL("/api/auth/google/callback", origin).toString();
}

/** Where the browser goes to sign in. */
export function authorizationUrl(settings: GoogleSettings, attempt: SignInAttempt, redirectUri: string): string {
  const url = new URL(settings.authUrl);
  url.search = new URLSearchParams({
    client_id: settings.clientId,
    redirect_uri: redirectUri,
    response_type: "code",
    // Who the person is, their email and their name. Nothing of theirs at Google is asked for.
    scope: "openid email profile",
    state: attempt.state,
    nonce: attempt.nonce,
    code_challenge: challengeOf(attempt.verifier),
    code_challenge_method: "S256",
    // Somebody with several Google accounts chooses one, instead of being signed in with whichever is first.
    prompt: "select_account",
  }).toString();
  return url.toString();
}

export function encodeAttempt(attempt: SignInAttempt): string {
  return Buffer.from(JSON.stringify(attempt)).toString("base64url");
}

const SECRET = /^[A-Za-z0-9_-]{20,128}$/;

/** What the cookie holds, if it holds an attempt. A cookie is input like any other: it is checked, not believed. */
export function decodeAttempt(value: string | undefined): SignInAttempt | null {
  if (!value || value.length > 2000) {
    return null;
  }
  try {
    const parsed: unknown = JSON.parse(Buffer.from(value, "base64url").toString("utf8"));
    if (typeof parsed !== "object" || parsed === null) {
      return null;
    }
    const { state, nonce, verifier, next } = parsed as Record<string, unknown>;
    if (
      typeof state !== "string" ||
      typeof nonce !== "string" ||
      typeof verifier !== "string" ||
      !SECRET.test(state) ||
      !SECRET.test(nonce) ||
      !SECRET.test(verifier)
    ) {
      return null;
    }
    return { state, nonce, verifier, next: safeNextPath(typeof next === "string" ? next : null) };
  } catch {
    return null;
  }
}

/** Google did not give an ID token for the code. */
export class GoogleExchangeError extends Error {}

/**
 * Exchanges the one-time code for an ID token. This is a call from this
 * server to Google, with the client secret: the browser never sees either.
 */
export async function exchangeCode(
  settings: GoogleSettings,
  code: string,
  verifier: string,
  redirectUri: string,
  request: typeof fetch = fetch,
): Promise<string> {
  let response: Response;
  try {
    response = await request(settings.tokenUrl, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
      body: new URLSearchParams({
        grant_type: "authorization_code",
        code,
        code_verifier: verifier,
        client_id: settings.clientId,
        client_secret: settings.clientSecret,
        redirect_uri: redirectUri,
      }),
      cache: "no-store",
      signal: AbortSignal.timeout(10_000),
    });
  } catch {
    throw new GoogleExchangeError("Google could not be reached");
  }
  if (!response.ok) {
    // The body names the reason (a used code, a wrong secret); the status is enough for the log.
    throw new GoogleExchangeError(`Google answered ${response.status}`);
  }
  const body: unknown = await response.json().catch(() => null);
  const idToken = typeof body === "object" && body !== null ? (body as Record<string, unknown>).id_token : undefined;
  if (typeof idToken !== "string" || idToken === "") {
    throw new GoogleExchangeError("Google answered without an ID token");
  }
  return idToken;
}
