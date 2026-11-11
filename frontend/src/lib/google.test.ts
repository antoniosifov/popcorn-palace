import { describe, expect, it, vi } from "vitest";

import { googleProblem } from "./errors";
import {
  authorizationUrl,
  callbackUrl,
  challengeOf,
  decodeAttempt,
  encodeAttempt,
  exchangeCode,
  GoogleExchangeError,
  googleSettings,
  newAttempt,
  type GoogleSettings,
} from "./google";

const SETTINGS: GoogleSettings = {
  clientId: "popcorn.apps.googleusercontent.com",
  clientSecret: "the-client-secret",
  authUrl: "https://accounts.google.com/o/oauth2/v2/auth",
  tokenUrl: "https://oauth2.googleapis.com/token",
};
const CALLBACK = "https://popcorn.example.com/api/auth/google/callback";

describe("whether signing in with Google is set up", () => {
  it("needs both the client id and its secret", () => {
    expect(googleSettings({})).toBeNull();
    expect(googleSettings({ GOOGLE_CLIENT_ID: "id" })).toBeNull();
    expect(googleSettings({ GOOGLE_CLIENT_SECRET: "secret" })).toBeNull();
    expect(googleSettings({ GOOGLE_CLIENT_ID: " ", GOOGLE_CLIENT_SECRET: "secret" })).toBeNull();
  });

  it("talks to Google unless told otherwise", () => {
    expect(googleSettings({ GOOGLE_CLIENT_ID: " id ", GOOGLE_CLIENT_SECRET: "secret" })).toEqual({
      clientId: "id",
      clientSecret: "secret",
      authUrl: "https://accounts.google.com/o/oauth2/v2/auth",
      tokenUrl: "https://oauth2.googleapis.com/token",
    });
  });
});

describe("an attempt to sign in", () => {
  it("is three secrets nobody could guess, new every time, and where to go afterwards", () => {
    const first = newAttempt("/showtimes/7");
    const second = newAttempt("/showtimes/7");

    for (const value of [first.state, first.nonce, first.verifier]) {
      expect(value).toMatch(/^[A-Za-z0-9_-]{43}$/);
    }
    expect(new Set([first.state, first.nonce, first.verifier, second.state, second.nonce, second.verifier]).size).toBe(
      6,
    );
    expect(first.next).toBe("/showtimes/7");
  });

  it("only ever leads back into this site", () => {
    expect(newAttempt("https://evil.example/").next).toBe("/");
    expect(newAttempt("//evil.example/").next).toBe("/");
    expect(newAttempt(null).next).toBe("/");
  });

  it("survives the cookie it is kept in", () => {
    const attempt = newAttempt("/bookings");

    expect(decodeAttempt(encodeAttempt(attempt))).toEqual(attempt);
  });

  it("is not read out of a cookie that is not one", () => {
    const attempt = newAttempt("/");
    const tampered = (change: Record<string, unknown>) =>
      Buffer.from(JSON.stringify({ ...attempt, ...change })).toString("base64url");

    for (const cookie of [
      undefined,
      "",
      "not base64 json",
      Buffer.from("[]").toString("base64url"),
      Buffer.from("null").toString("base64url"),
      tampered({ state: "short" }),
      tampered({ nonce: 5 }),
      tampered({ verifier: "has spaces and is long enough to pass on length alone" }),
      "x".repeat(3000),
    ]) {
      expect(decodeAttempt(cookie), String(cookie).slice(0, 40)).toBeNull();
    }
    // A destination outside the site is not an error, just not followed.
    expect(decodeAttempt(tampered({ next: "https://evil.example/" }))?.next).toBe("/");
  });
});

describe("the address the browser is sent to", () => {
  it("gives Google the hash of the verifier, never the verifier", () => {
    // The example of RFC 7636, appendix B.
    expect(challengeOf("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")).toBe(
      "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
    );
  });

  it("describes this attempt and asks for no more than who the person is", () => {
    const attempt = newAttempt("/");

    const url = new URL(authorizationUrl(SETTINGS, attempt, CALLBACK));

    expect(url.origin + url.pathname).toBe("https://accounts.google.com/o/oauth2/v2/auth");
    expect(Object.fromEntries(url.searchParams)).toEqual({
      client_id: SETTINGS.clientId,
      redirect_uri: CALLBACK,
      response_type: "code",
      scope: "openid email profile",
      state: attempt.state,
      nonce: attempt.nonce,
      code_challenge: challengeOf(attempt.verifier),
      code_challenge_method: "S256",
      prompt: "select_account",
    });
    expect(url.toString()).not.toContain(attempt.verifier);
    expect(url.toString()).not.toContain(SETTINGS.clientSecret);
  });

  it("comes back to this site, wherever the site is", () => {
    expect(callbackUrl(new URL("https://popcorn.example.com"))).toBe(CALLBACK);
    expect(callbackUrl(new URL("http://localhost:3000"))).toBe("http://localhost:3000/api/auth/google/callback");
  });
});

describe("exchanging the code", () => {
  const answer = (status: number, body: unknown) =>
    vi.fn(async () => new Response(typeof body === "string" ? body : JSON.stringify(body), { status }));

  it("sends the code with the verifier and the client secret, and returns the ID token", async () => {
    const request = answer(200, { id_token: "the.id.token", access_token: "unused" });

    const idToken = await exchangeCode(
      SETTINGS,
      "the-code",
      "the-verifier",
      CALLBACK,
      request as unknown as typeof fetch,
    );

    expect(idToken).toBe("the.id.token");
    const [url, init] = request.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(SETTINGS.tokenUrl);
    expect(init.method).toBe("POST");
    expect(Object.fromEntries(init.body as URLSearchParams)).toEqual({
      grant_type: "authorization_code",
      code: "the-code",
      code_verifier: "the-verifier",
      client_id: SETTINGS.clientId,
      client_secret: SETTINGS.clientSecret,
      redirect_uri: CALLBACK,
    });
  });

  it("fails when Google refuses, answers without a token, answers nonsense, or does not answer", async () => {
    const unreachable = vi.fn(async () => {
      throw new TypeError("fetch failed");
    });
    for (const request of [
      answer(400, { error: "invalid_grant" }),
      answer(200, { access_token: "no id token" }),
      answer(200, { id_token: "" }),
      answer(200, "<html>not json</html>"),
      unreachable,
    ]) {
      await expect(
        exchangeCode(SETTINGS, "code", "verifier", CALLBACK, request as unknown as typeof fetch),
      ).rejects.toBeInstanceOf(GoogleExchangeError);
    }
  });

  it("does not repeat what Google said about the failure, which can name the secret", async () => {
    const request = answer(401, { error: "invalid_client", error_description: "the-client-secret is wrong" });

    await expect(
      exchangeCode(SETTINGS, "code", "verifier", CALLBACK, request as unknown as typeof fetch),
    ).rejects.toThrow("Google answered 401");
  });
});

describe("the reason a sign-in ended on the login form", () => {
  it("is put into words only when it is one of the known ones", () => {
    expect(googleProblem("GOOGLE_SIGN_IN_FAILED")).toBe("Signing in with Google did not work. Try again.");
    expect(googleProblem("GOOGLE_EMAIL_UNVERIFIED")).toContain("has not confirmed");
    expect(googleProblem("EMAIL_TAKEN")).toContain("another Google account");
    expect(googleProblem("TOO_MANY_REQUESTS")).toContain("Too many attempts");
  });

  it("is nothing for anything else somebody writes into the address", () => {
    for (const code of ["", "INTERNAL_ERROR", "<script>alert(1)</script>", "Your account was deleted"]) {
      expect(googleProblem(code), code).toBeUndefined();
    }
  });
});
