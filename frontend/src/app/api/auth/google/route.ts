import { connection, NextResponse, type NextRequest } from "next/server";

import { secureCookies } from "@/lib/config";
import {
  ATTEMPT_COOKIE,
  ATTEMPT_COOKIE_PATH,
  ATTEMPT_SECONDS,
  authorizationUrl,
  callbackUrl,
  encodeAttempt,
  googleSettings,
  newAttempt,
} from "@/lib/google";
import { siteOrigin } from "@/lib/site";

/**
 * "Continue with Google": remembers this attempt in a cookie and sends the
 * browser to Google. Where signing in with Google is not set up, this address
 * does not exist.
 */
export async function GET(request: NextRequest) {
  // Whether Google sign-in is set up is a setting of the running server, not of the build. Without
  // this, the build would run the handler once, find nothing configured, and serve that 404 for ever.
  await connection();
  const settings = googleSettings();
  if (!settings) {
    return new NextResponse(null, { status: 404 });
  }
  const attempt = newAttempt(request.nextUrl.searchParams.get("next"));
  const origin = siteOrigin(process.env.SITE_URL, request.headers);

  const response = NextResponse.redirect(authorizationUrl(settings, attempt, callbackUrl(origin)));
  response.cookies.set(ATTEMPT_COOKIE, encodeAttempt(attempt), {
    httpOnly: true,
    // Lax, not Strict: the way back from Google is a navigation started by another site, and it needs this cookie.
    sameSite: "lax",
    secure: secureCookies(),
    path: ATTEMPT_COOKIE_PATH,
    maxAge: ATTEMPT_SECONDS,
  });
  return response;
}
