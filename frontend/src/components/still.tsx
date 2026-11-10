import { atWidth, safePicture, srcSet } from "@/lib/artwork";

/**
 * The still that fills a first screen: a wide frame of the movie, edge to
 * edge, opening like a shutter. Under it a neutral scrim, where the words go.
 * A movie without a still gets the flat ground and the words alone.
 */
export function HeroStill({ title, url }: { title: string; url: string | null | undefined }) {
  const picture = safePicture(url);
  if (!picture) {
    return null;
  }
  return (
    <>
      <div className="shutter absolute inset-0">
        {/* Sized and served by TMDB's picture servers; see lib/artwork.ts. */}
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          className="hero-picture"
          src={atWidth(picture, 1280)}
          srcSet={srcSet(picture, "backdrop")}
          sizes="100vw"
          alt={`A still from ${title}`}
          width={1280}
          height={720}
          fetchPriority="high"
          decoding="async"
        />
      </div>
      <div className="hero-scrim" aria-hidden="true" />
    </>
  );
}

/** The class that sets a title at a size it fits in two lines at, by its length. */
export function displaySize(title: string): string {
  if (title.length <= 12) {
    return "display-short";
  }
  return title.length <= 24 ? "display-medium" : "display-long";
}
