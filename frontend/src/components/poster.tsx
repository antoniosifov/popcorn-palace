import { atWidth, safePicture, srcSet } from "@/lib/artwork";

/**
 * A movie's poster. When the catalog has no artwork for a movie (the built-in
 * catalog has none, and a movie added by hand may have none) a title card is
 * drawn from its name instead: a monogram of its first two letters on one of
 * three greys, the same for the same title every time.
 */

const TONES = [
  { ground: "#232323", letter: "#474747" },
  { ground: "#2a2a2a", letter: "#505050" },
  { ground: "#313131", letter: "#5a5a5a" },
];

function hash(text: string): number {
  let value = 0;
  for (let index = 0; index < text.length; index++) {
    value = (value * 31 + text.charCodeAt(index)) >>> 0;
  }
  return value;
}

/**
 * The first two letters or digits of the title, skipping a leading article:
 * "In" for Interstellar, "Gr" for The Grand Budapest Hotel. Two, because a
 * lone "I" or "L" is a bare stroke that reads as a mistake, not as a letter.
 */
export function monogram(title: string): string {
  const stripped = title.replace(/^(the|a|an)\s+/i, "");
  const characters = stripped.match(/[\p{L}\p{N}]/gu) ?? [];
  const [first = "P", second = ""] = characters;
  return first.toUpperCase() + second.toLowerCase();
}

type Props = {
  title: string;
  /** The https address of the poster, when there is one. */
  url?: string | null;
  /** How wide the poster is shown, as the `sizes` of a picture: "168px", "(min-width: 768px) 220px, 40vw". */
  sizes: string;
  /** Sets the width; the frame keeps the 2:3 of a poster. */
  className?: string;
  /** The first poster of a page is fetched at once; the rest when they come near the screen. */
  eager?: boolean;
};

export function Poster({ title, url, sizes, className = "", eager = false }: Props) {
  const picture = safePicture(url);
  if (picture) {
    return (
      <span className={`poster ${className}`}>
        {/* Sized and served by TMDB's picture servers; passing it through this server would only re-encode it. */}
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={atWidth(picture, 342)}
          srcSet={srcSet(picture, "poster")}
          sizes={sizes}
          alt={`Poster of ${title}`}
          width={342}
          height={513}
          loading={eager ? "eager" : "lazy"}
          decoding="async"
        />
      </span>
    );
  }

  const tone = TONES[hash(title) % TONES.length];
  return (
    <span
      aria-hidden="true"
      className={`poster flex items-end justify-center [container-type:inline-size] ${className}`}
      style={{ background: tone.ground }}
    >
      {/* Set to the width of the frame and sunk a little below it, so the frame crops the foot of the letters. */}
      <span
        className="mb-[-0.12em] font-[family-name:var(--font-display)] text-[70cqw] font-normal leading-none tracking-[-0.04em]"
        style={{ color: tone.letter }}
      >
        {monogram(title)}
      </span>
    </span>
  );
}
