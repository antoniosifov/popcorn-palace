import { afterEach, describe, expect, it, vi } from "vitest";

import { api, ApiError, API_UNREACHABLE, apiUrl } from "./client";

function respond(status: number, body?: unknown, headers: Record<string, string> = {}) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json", ...headers },
  });
}

/** Replaces fetch for one test and returns the calls it received. */
function stubFetch(answer: Response | Error) {
  const fetchMock = vi.fn<typeof fetch>(async () => {
    if (answer instanceof Error) {
      throw answer;
    }
    return answer;
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

/** The error a call fails with. */
async function failureOf(call: Promise<unknown>): Promise<ApiError> {
  try {
    await call;
  } catch (error) {
    return error as ApiError;
  }
  throw new Error("The call was expected to fail");
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("calling the API", () => {
  it("builds addresses under /api/v1 of the configured API and leaves out empty filters", () => {
    vi.stubEnv("API_URL", "http://api:8080/");

    expect(apiUrl("/showtimes", { from: "2030-01-15T00:00:00.000Z", movieId: undefined, page: 0 })).toBe(
      "http://api:8080/api/v1/showtimes?from=2030-01-15T00%3A00%3A00.000Z&page=0",
    );
  });

  it("sends the caller's token and the body as JSON, and never reuses a stored answer", async () => {
    const fetchMock = stubFetch(respond(201, { id: "h1" }));

    const result = await api<{ id: string }>("/showtimes/1/holds", {
      method: "POST",
      body: { seatIds: [1, 2] },
      token: "the-token",
      headers: { "Idempotency-Key": "key-1", "X-Forwarded-For": undefined },
    });

    expect(result).toEqual({ id: "h1" });
    const [, init] = fetchMock.mock.calls[0];
    const headers = new Headers(init?.headers);
    expect(init?.method).toBe("POST");
    expect(init?.body).toBe('{"seatIds":[1,2]}');
    expect(init?.cache).toBe("no-store");
    expect(headers.get("Authorization")).toBe("Bearer the-token");
    expect(headers.get("Content-Type")).toBe("application/json");
    expect(headers.get("Idempotency-Key")).toBe("key-1");
    expect(headers.has("X-Forwarded-For")).toBe(false);
  });

  it("sends no Authorization header for an anonymous call", async () => {
    const fetchMock = stubFetch(respond(200, { content: [] }));

    await api("/movies");

    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).has("Authorization")).toBe(false);
  });

  it("returns nothing for 204", async () => {
    stubFetch(new Response(null, { status: 204 }));

    await expect(api("/showtimes/1/holds/h1", { method: "DELETE", token: "t" })).resolves.toBeUndefined();
  });
});

describe("failures", () => {
  it("carry the status, the code and everything the API said about the problem", async () => {
    stubFetch(
      respond(409, {
        title: "Conflict",
        status: 409,
        detail: "Some of the seats are being booked by another customer",
        code: "SEAT_ALREADY_HELD",
        seatIds: [101],
        traceId: "4bf92f3577b34da6a3ce929d0e0e4736",
      }),
    );

    const error = await failureOf(api("/showtimes/1/holds", { method: "POST", body: {} }));

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(409);
    expect(error.code).toBe("SEAT_ALREADY_HELD");
    expect(error.problem.seatIds).toEqual([101]);
    expect(error.problem.traceId).toBe("4bf92f3577b34da6a3ce929d0e0e4736");
    expect(error.message).toBe("Some of the seats are being booked by another customer");
  });

  it("take the trace id from the header when the body has none", async () => {
    stubFetch(new Response("<html>Bad gateway</html>", { status: 502, headers: { "X-Trace-Id": "abc123" } }));

    const error = await failureOf(api("/movies"));

    expect(error.status).toBe(502);
    expect(error.code).toBe("UNKNOWN");
    expect(error.problem.traceId).toBe("abc123");
  });

  it("are reported as the service being unreachable when no answer comes back", async () => {
    stubFetch(new TypeError("fetch failed"));

    const error = await failureOf(api("/movies"));

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(503);
    expect(error.code).toBe(API_UNREACHABLE);
  });

  it("let an aborted request through as an abort, not as an outage", async () => {
    stubFetch(Object.assign(new Error("aborted"), { name: "AbortError" }));

    const error = await failureOf(api("/movies"));

    expect(error).not.toBeInstanceOf(ApiError);
    expect(error.name).toBe("AbortError");
  });
});
