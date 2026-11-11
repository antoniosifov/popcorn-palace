import { connection, NextResponse, type NextRequest } from "next/server";

import { api, ApiError } from "@/lib/api/client";
import type { AuthResponse } from "@/lib/api/types";
import {
  ATTEMPT_COOKIE,
  ATTEMPT_COOKIE_PATH,
  callbackUrl,
  decodeAttempt,
  exchangeCode,
  GoogleExchangeError,
  googleSettings,
} from "@/lib/google";
import { loginPath } from "@/lib/paths";
import { startSession } from "@/lib/session";
import { siteOrigin } from "@/lib/site";

/** Reasons the login form knows how to put into words (see `lib/errors.ts`). */
const TOLD = new Set(["GOOGLE_EMAIL_UNVERIFIED", "EMAIL_TAKEN", "TOO_MANY_REQUESTS"]);

/**
 * Google sends the browser back here with a one-time code. The code is worth
 * something only together with what this server kept: the attempt in the
 * cookie (its state must match) and the client secret. The ID token it buys
 * is handed to the API, which checks it and answers with a session.
 *
 * Every way this can go wrong ends on the login form with one sentence; what
 * exactly went wrong is in the logs, not in the address bar.
 */
export async function GET(request: NextRequest) {
  // Whether Google sign-in is set up is a setting of the running server, not of the build. Without
  // this, the build would run the handler once, find nothing configured, and serve that 404 for ever.
  await connection();
  const settings = googleSettings();
  if (!settings) {
    return new NextResponse(null, { status: 404 });
  }
  const origin = siteOrigin(process.env.SITE_URL, request.headers);
  const query = request.nextUrl.searchParams;
  const attempt = decodeAttempt(request.cookies.get(ATTEMPT_COOKIE)?.value);

  const leave = (path: string) => {
    const response = NextResponse.redirect(new URL(path, origin));
    // One attempt, one answer: whatever happened, this cookie has done its work. A date in the
    // past removes it; "max-age 0" alone is left out of the header and would leave an empty cookie behind.
    response.cookies.set(ATTEMPT_COOKIE, "", { path: ATTEMPT_COOKIE_PATH, expires: new Date(0) });
    return response;
  };
  const backToLogin = (reason?: string) => {
    const login = new URL(loginPath(attempt?.next ?? "/"), origin);
    if (reason) {
      login.searchParams.set("google", reason);
    }
    return leave(login.pathname + login.search);
  };

  // The person pressed "Cancel" at Google: nothing went wrong, so nothing is said.
  if (query.get("error") === "access_denied") {
    return backToLogin();
  }
  const code = query.get("code");
  if (!attempt || !code || query.get("error") || query.get("state") !== attempt.state) {
    console.warn("Google sign-in: the answer does not belong to an attempt of this browser");
    return backToLogin("GOOGLE_SIGN_IN_FAILED");
  }

  let auth: AuthResponse;
  try {
    const idToken = await exchangeCode(settings, code, attempt.verifier, callbackUrl(origin));
    auth = await api<AuthResponse>("/auth/google", {
      method: "POST",
      body: { idToken, nonce: attempt.nonce },
      // The API limits attempts per visitor, and sees this server, not the visitor.
      headers: { "X-Forwarded-For": request.headers.get("x-forwarded-for") ?? undefined },
    });
  } catch (error) {
    if (error instanceof ApiError) {
      console.warn(`Google sign-in: the API refused it (${error.code})`);
      return backToLogin(error.code && TOLD.has(error.code) ? error.code : "GOOGLE_SIGN_IN_FAILED");
    }
    if (error instanceof GoogleExchangeError) {
      console.warn(`Google sign-in: ${error.message}`);
      return backToLogin("GOOGLE_SIGN_IN_FAILED");
    }
    throw error;
  }

  await startSession(auth);
  return leave(attempt.next);
}
