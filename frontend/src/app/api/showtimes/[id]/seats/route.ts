import type { NextRequest } from "next/server";

import { getMyHold } from "@/lib/api/bookings";
import { getSeatMap } from "@/lib/api/catalog";
import { ApiError } from "@/lib/api/client";
import { sessionToken } from "@/lib/session";

/**
 * The seat map as it is right now, with the caller's own hold. The page asks
 * for it again whenever its live stream reconnects, because changes made
 * while it was away are not replayed.
 */
export async function GET(_request: NextRequest, context: RouteContext<"/api/showtimes/[id]/seats">) {
  const { id } = await context.params;
  const showtimeId = Number(id);
  if (!Number.isSafeInteger(showtimeId) || showtimeId <= 0) {
    return Response.json({ code: "SHOWTIME_NOT_FOUND" }, { status: 404 });
  }
  const token = await sessionToken();
  try {
    const [map, hold] = await Promise.all([
      getSeatMap(showtimeId, token),
      token ? getMyHold(showtimeId, token) : Promise.resolve(null),
    ]);
    return Response.json({ map, hold }, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    if (error instanceof ApiError) {
      return Response.json({ code: error.code }, { status: error.status });
    }
    throw error;
  }
}
