import type { NextRequest } from "next/server";

import { apiUrl } from "@/lib/api/client";

/**
 * The live seat stream of a showtime (server-sent events), passed through
 * from the API byte for byte. The browser only ever talks to this origin, so
 * it needs no address of the API and no cross-origin permission.
 *
 * When the browser goes away the request is aborted, which closes the
 * connection to the API as well: no stream is left open behind a closed tab.
 */
export async function GET(request: NextRequest, context: RouteContext<"/api/showtimes/[id]/seats/stream">) {
  const { id } = await context.params;
  if (!/^[1-9]\d{0,15}$/.test(id)) {
    return new Response(null, { status: 404 });
  }

  let upstream: Response;
  try {
    upstream = await fetch(apiUrl(`/showtimes/${id}/seats/stream`), {
      headers: { Accept: "text/event-stream" },
      cache: "no-store",
      signal: request.signal,
    });
  } catch {
    // The API is unreachable; EventSource tries again by itself.
    return new Response(null, { status: 503 });
  }
  if (!upstream.ok || !upstream.body) {
    return new Response(null, { status: upstream.status === 404 ? 404 : 502 });
  }

  return new Response(upstream.body, {
    headers: {
      "Content-Type": "text/event-stream; charset=utf-8",
      // no-transform keeps compression from holding events back until a buffer fills.
      "Cache-Control": "no-cache, no-transform",
      // The same request to a reverse proxy in front (nginx honours this header).
      "X-Accel-Buffering": "no",
    },
  });
}
