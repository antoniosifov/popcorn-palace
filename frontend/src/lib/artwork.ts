/**
 * Posters and stills come from TMDB's picture servers, which serve every
 * picture at a handful of fixed widths: the width is a part of the address.
 * So the browser is offered the same picture at several widths and takes the
 * one that fits the screen, and this application never downloads, resizes or
 * stores a picture itself.
 */

export type ArtworkKind = "poster" | "backdrop";

/** The widths TMDB serves each kind of picture at, smallest first. */
const WIDTHS: Record<ArtworkKind, number[]> = {
  poster: [185, 342, 500, 780],
  backdrop: [780, 1280],
};

/** A TMDB picture address: https://image.tmdb.org/t/p/w500/abc.jpg */
const TMDB_PICTURE = /^(https:\/\/image\.tmdb\.org\/t\/p\/)(w\d+|original)(\/[^?#\s]+)$/;

export function isSizedPicture(url: string): boolean {
  return TMDB_PICTURE.test(url);
}

/** The same picture at another width. An address that is not TMDB's is returned as it is. */
export function atWidth(url: string, width: number | "original"): string {
  const size = width === "original" ? "original" : `w${width}`;
  return url.replace(TMDB_PICTURE, (_match, base: string, _size: string, file: string) => `${base}${size}${file}`);
}

/**
 * What goes into `srcset`: every width the picture exists at. A still is also
 * offered in its original size, for screens wider than the largest fixed
 * width; originals are 1920 pixels wide or more. Undefined for a picture that
 * exists at one size only.
 */
export function srcSet(url: string, kind: ArtworkKind): string | undefined {
  if (!isSizedPicture(url)) {
    return undefined;
  }
  const candidates = WIDTHS[kind].map((width) => `${atWidth(url, width)} ${width}w`);
  if (kind === "backdrop") {
    candidates.push(`${atWidth(url, "original")} 1920w`);
  }
  return candidates.join(", ");
}

/**
 * Only https pictures are ever put on a page. The API refuses anything else
 * when a movie is saved; this is the same rule at the point of use.
 */
export function safePicture(url: string | null | undefined): string | null {
  return url && /^https:\/\/[^\s"'<>]+$/.test(url) ? url : null;
}

/** The same rule for a link to a trailer. */
export function safeLink(url: string | null | undefined): string | null {
  return safePicture(url);
}

/**
 * The id of a YouTube video, from the address of its page: the trailers TMDB
 * lists are YouTube's. Null for anything else, and for an id that does not
 * look like one, since it goes into the address of a player.
 */
export function youTubeId(url: string): string | null {
  let address: URL;
  try {
    address = new URL(url);
  } catch {
    return null;
  }
  if (address.protocol !== "https:") {
    return null;
  }
  let id: string | null = null;
  if (["www.youtube.com", "youtube.com", "m.youtube.com"].includes(address.hostname) && address.pathname === "/watch") {
    id = address.searchParams.get("v");
  } else if (address.hostname === "youtu.be") {
    id = address.pathname.slice(1);
  }
  return id && /^[A-Za-z0-9_-]{6,20}$/.test(id) ? id : null;
}
