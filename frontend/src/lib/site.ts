/**
 * The address the site is reached at, without a path. A link that is shared
 * is previewed from addresses the page gives about itself (its picture, most
 * of all), and those have to be complete: a messenger does not know what
 * "/opengraph-image.png" is relative to.
 *
 * `configured` is SITE_URL, set where the address is known, as on a server.
 * Without it, the address the request came to is used: the host the visitor
 * asked for, and https when the proxy in front says so.
 */
export function siteOrigin(configured: string | undefined, requestHeaders: Headers): URL {
  if (configured) {
    const url = parse(configured);
    if (url) {
      return url;
    }
  }
  const host = requestHeaders.get("x-forwarded-host") ?? requestHeaders.get("host");
  const secure = requestHeaders.get("x-forwarded-proto")?.split(",")[0].trim() === "https";
  return (host ? parse(`${secure ? "https" : "http"}://${host}`) : null) ?? new URL("http://localhost:3000");
}

/** An http or https address reduced to its origin, or nothing. */
function parse(address: string): URL | null {
  try {
    const url = new URL(address);
    return url.protocol === "https:" || url.protocol === "http:" ? new URL(url.origin) : null;
  } catch {
    return null;
  }
}

/** A description short enough for a preview: whole words, and a mark that it goes on. */
export function previewText(text: string, limit = 200): string {
  const plain = text.replace(/\s+/g, " ").trim();
  if (plain.length <= limit) {
    return plain;
  }
  const cut = plain.slice(0, limit);
  const lastSpace = cut.lastIndexOf(" ");
  return `${(lastSpace > limit / 2 ? cut.slice(0, lastSpace) : cut).replace(/[\s.,;:!?-]+$/, "")}…`;
}
