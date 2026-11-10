import Link from "next/link";

/**
 * The mark: a small block of seats with one of them lit, the one that is
 * yours. The same picture the product draws at full size on the seat map.
 */
export function Mark({ size = 20 }: { size?: number }) {
  const seats = [0, 1, 2].flatMap((row) => [0, 1, 2].map((column) => ({ row, column })));
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" aria-hidden="true" focusable="false">
      {seats.map(({ row, column }) => (
        <rect
          key={`${row}-${column}`}
          x={column * 7}
          y={row * 7}
          width="6"
          height="6"
          rx="1.5"
          fill={row === 1 && column === 1 ? "var(--color-accent)" : "var(--color-control)"}
        />
      ))}
    </svg>
  );
}

export function Wordmark() {
  return (
    <Link
      href="/"
      className="flex flex-none items-center gap-3 font-[family-name:var(--font-display)] text-[22px] tracking-[-0.01em]"
    >
      <Mark />
      Popcorn Palace
    </Link>
  );
}
