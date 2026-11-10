import { NextResponse, type NextRequest } from "next/server";

import { SESSION_COOKIE } from "@/lib/session-cookie";

/**
 * A first, cheap check before a page that needs an account is rendered: no
 * session cookie, no page, straight to the login form and back afterwards.
 * It only looks at whether a cookie is there. Whether the token in it is
 * valid, and what its owner may do, is decided where the data is read.
 */
export function proxy(request: NextRequest) {
  if (request.cookies.has(SESSION_COOKIE)) {
    return NextResponse.next();
  }
  const login = new URL("/login", request.url);
  login.searchParams.set("next", request.nextUrl.pathname + request.nextUrl.search);
  return NextResponse.redirect(login);
}

export const config = {
  matcher: ["/bookings/:path*", "/admin/:path*"],
};
