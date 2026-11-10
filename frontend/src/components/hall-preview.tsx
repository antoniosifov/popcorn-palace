import type { SeatCategory } from "@/lib/api/types";

/**
 * A hall's layout as a picture: the same marks the seat map uses, with no
 * prices and nothing to click. Shown while a hall is being designed and on
 * the page of an existing one.
 */
export function HallPreview({ rows }: { rows: { row: number; category: SeatCategory; seats: number }[] }) {
  if (rows.length === 0) {
    return (
      <div className="rounded-inner border border-dashed border-control px-4 py-6 text-center text-muted">
        Add rows and seats to see the hall.
      </div>
    );
  }
  const widest = Math.max(...rows.map((row) => row.seats));
  const maxWidth = widest * 22 + (widest - 1) * 3 + 2 * 26;
  return (
    <div className="mx-auto w-full" style={{ maxWidth }} role="img" aria-label={`A hall of ${rows.length} rows`}>
      <svg viewBox="0 0 400 26" className="mx-auto mb-1 block w-[86%]" aria-hidden="true" focusable="false">
        <path d="M4 22 Q200 -10 396 22" fill="none" stroke="var(--color-faint)" strokeWidth="2" strokeLinecap="round" />
      </svg>
      <p className="mb-3 text-center text-small text-faint">Screen</p>
      <div className="grid gap-[3px]">
        {rows.map((row) => (
          <div key={row.row} className="flex items-center gap-1.5">
            <span className="num w-5 flex-none text-right text-small text-faint">{row.row}</span>
            <div className="grid flex-1 gap-[3px]" style={{ gridTemplateColumns: `repeat(${widest}, minmax(0, 1fr))` }}>
              {Array.from({ length: row.seats }, (_, index) => (
                <span
                  key={index}
                  className={row.category === "VIP" ? "seat seat-vip" : "seat"}
                  style={index === 0 ? { gridColumnStart: Math.floor((widest - row.seats) / 2) + 1 } : undefined}
                />
              ))}
            </div>
            <span className="w-5 flex-none" />
          </div>
        ))}
      </div>
    </div>
  );
}
