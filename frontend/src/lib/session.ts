import "server-only";

import { cookies } from "next/headers";

import { secureCookies } from "./config";
import { SESSION_COOKIE } from "./session-cookie";
import type { AuthResponse } from "./api/types";

/**
 * The session is the API's access token, kept in a cookie that scripts in the
 * page cannot read (HttpOnly) and that is not sent on requests started by
 * other sites (SameSite=Lax). The cookie lives exactly as long as the token.
 */
export async function sessionToken(): Promise<string | undefined> {
  return (await cookies()).get(SESSION_COOKIE)?.value || undefined;
}

/** Only in Server Actions and Route Handlers: cookies cannot be set while rendering. */
export async function startSession(auth: AuthResponse): Promise<void> {
  (await cookies()).set(SESSION_COOKIE, auth.accessToken, {
    httpOnly: true,
    sameSite: "lax",
    secure: secureCookies(),
    path: "/",
    maxAge: Math.max(0, Math.floor(auth.expiresIn)),
  });
}

export async function endSession(): Promise<void> {
  (await cookies()).delete(SESSION_COOKIE);
}
