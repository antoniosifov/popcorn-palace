/**
 * The API holds no artwork, so every movie gets a title card drawn from its
 * own name: its initial, large and cropped by the frame, on one of three greys. The same
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

/** The first letter or digit of the title, skipping a leading article. */
function initial(title: string): string {
  const stripped = title.replace(/^(the|a|an)\s+/i, "");
  const match = /[\p{L}\p{N}]/u.exec(stripped);
  return (match?.[0] ?? "P").toUpperCase();
}

type Size = "row" | "header" | "large";

const SIZES: Record<Size, { box: string; letter: string }> = {
  row: { box: "h-[75px] w-[50px] rounded-[6px]", letter: "text-[64px]" },
  header: { box: "h-[62px] w-[50px] rounded-[6px]", letter: "text-[56px]" },
  large: { box: "h-[180px] w-[120px] rounded-[10px]", letter: "text-[156px]" },
};

export function Poster({ title, size = "row" }: { title: string; size?: Size }) {
  const tone = TONES[hash(title) % TONES.length];
  const { box, letter } = SIZES[size];
  return (
    <div
      aria-hidden="true"
      className={`relative flex flex-none items-end justify-center overflow-hidden ${box}`}
      style={{ background: tone.ground }}
    >
      {/* Set larger than the frame and sunk a little below it, so the frame crops the letter. */}
      <span
        className={`mb-[-0.14em] font-semibold leading-none tracking-[-0.04em] ${letter}`}
        style={{ color: tone.letter }}
      >
        {initial(title)}
      </span>
    </div>
  );
}
