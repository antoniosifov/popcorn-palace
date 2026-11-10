/**
 * The API holds no artwork, so every movie gets a title card drawn from its
 * own name: a monogram of its first two letters, cropped by the frame, on one of three greys. The same
 * title always gets the same card. Lightness only: the one hue of the product
 * is kept for the customer's seats.
 */

const TONES = [
  { ground: "#1c1c1c", letter: "#3a3a3a" },
  { ground: "#242424", letter: "#454545" },
  { ground: "#2c2c2c", letter: "#505050" },
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

type Size = "row" | "header" | "large";

const SIZES: Record<Size, { box: string; letters: string }> = {
  row: { box: "h-[75px] w-[50px] rounded-[6px]", letters: "text-[34px]" },
  header: { box: "h-[62px] w-[50px] rounded-[6px]", letters: "text-[32px]" },
  large: { box: "h-[180px] w-[120px] rounded-[10px]", letters: "text-[84px]" },
};

export function Poster({ title, size = "row" }: { title: string; size?: Size }) {
  const tone = TONES[hash(title) % TONES.length];
  const { box, letters } = SIZES[size];
  return (
    <div
      aria-hidden="true"
      className={`relative flex flex-none items-end justify-center overflow-hidden ${box}`}
      style={{ background: tone.ground }}
    >
      {/* Set to the width of the frame and sunk a little below it, so the frame crops the foot of the letters. */}
      <span
        className={`mb-[-0.12em] font-semibold leading-none tracking-[-0.05em] ${letters}`}
        style={{ color: tone.letter }}
      >
        {monogram(title)}
      </span>
    </div>
  );
}
