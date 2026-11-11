import { describe, expect, it } from "vitest";

import { previewText, siteOrigin } from "./site";

const request = (headers: Record<string, string>) => new Headers(headers);

describe("the address the site gives for itself", () => {
  it("is the configured one where there is one, whatever the request says", () => {
    const origin = siteOrigin("https://popcorn.example.com/", request({ host: "evil.example" }));

    expect(origin.href).toBe("https://popcorn.example.com/");
  });

  it("is otherwise the host the visitor asked for, over https when the proxy in front says so", () => {
    expect(siteOrigin(undefined, request({ host: "popcorn.example.com", "x-forwarded-proto": "https" })).href).toBe(
      "https://popcorn.example.com/",
    );
    expect(siteOrigin(undefined, request({ host: "localhost:3000" })).href).toBe("http://localhost:3000/");
    expect(siteOrigin("", request({ host: "localhost:3100", "x-forwarded-proto": "http" })).href).toBe(
      "http://localhost:3100/",
    );
  });

  it("is never something that is not the address of a site", () => {
    for (const configured of ["javascript:alert(1)", "not an address", "ftp://example.com"]) {
      expect(siteOrigin(configured, request({ host: "localhost:3000" })).href, configured).toBe(
        "http://localhost:3000/",
      );
    }
    expect(siteOrigin(undefined, request({ host: "a b" })).href).toBe("http://localhost:3000/");
    expect(siteOrigin(undefined, request({})).href).toBe("http://localhost:3000/");
  });

  it("keeps nothing but the origin", () => {
    expect(siteOrigin("https://popcorn.example.com/some/path?x=1", request({})).href).toBe(
      "https://popcorn.example.com/",
    );
  });
});

describe("the description in a preview", () => {
  it("is the text itself when it is short", () => {
    expect(previewText("  A heist,\n told twice. ")).toBe("A heist, told twice.");
  });

  it("is cut at a word, with a mark that it goes on", () => {
    const text =
      "One night in a city that never quite sleeps, a projectionist finds a reel that is not in any catalogue.";

    expect(previewText(text, 60)).toBe("One night in a city that never quite sleeps, a…");
    expect(previewText(text, 60).length).toBeLessThanOrEqual(61);
  });
});
