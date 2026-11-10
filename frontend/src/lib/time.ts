/**
 * Times of a cinema are times on the cinema's clock, wherever the visitor or
 * the server happens to be. The API speaks in UTC instants; everything here
 * converts between those and wall-clock time in one named zone, using only
 * Intl, so the server and the browser format a moment identically.
 */

const LOCALE = "en-GB";

/** A calendar day on the cinema's clock, "2030-01-15". */
export type DayKey = string;
/** Wall-clock date and time without a zone, "2030-01-15T18:00": what a datetime-local input holds. */
export type LocalDateTime = string;

type Parts = { year: number; month: number; day: number; hour: number; minute: number; second: number };

const partsFormatters = new Map<string, Intl.DateTimeFormat>();

function wallClock(instant: Date, zone: string): Parts {
  let formatter = partsFormatters.get(zone);
  if (!formatter) {
    formatter = new Intl.DateTimeFormat("en-US", {
      timeZone: zone,
      hourCycle: "h23",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
    partsFormatters.set(zone, formatter);
  }
  const parts: Record<string, number> = {};
  for (const part of formatter.formatToParts(instant)) {
    if (part.type !== "literal") {
      parts[part.type] = Number(part.value);
    }
  }
  return parts as unknown as Parts;
}

/** How far the zone is ahead of UTC at that instant, in milliseconds. */
function offsetAt(instant: Date, zone: string): number {
  const p = wallClock(instant, zone);
  const asIfUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
  return asIfUtc - Math.floor(instant.getTime() / 1000) * 1000;
}

export function isValidZone(zone: string): boolean {
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: zone });
    return true;
  } catch {
    return false;
  }
}

const pad = (value: number) => String(value).padStart(2, "0");

/** The calendar day an instant falls on, on the zone's clock. */
export function dayKey(instant: string | Date, zone: string): DayKey {
  const p = wallClock(new Date(instant), zone);
  return `${p.year}-${pad(p.month)}-${pad(p.day)}`;
}

/** The instant as wall-clock date and time in the zone, for a datetime-local input. */
export function toLocalDateTime(instant: string | Date, zone: string): LocalDateTime {
  const p = wallClock(new Date(instant), zone);
  return `${p.year}-${pad(p.month)}-${pad(p.day)}T${pad(p.hour)}:${pad(p.minute)}`;
}

/**
 * The instant at which the zone's clocks show the given date and time.
 * A time skipped when daylight saving starts (02:30 on that night) is read as
 * the same moment on the new clock, one hour later; a time that happens twice
 * when it ends resolves to one of the two. Returns null when the text is not a
 * date and time.
 */
export function fromLocalDateTime(local: LocalDateTime, zone: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(local);
  if (!match) {
    return null;
  }
  const [year, month, day, hour, minute, second] = match.slice(1).map((part) => Number(part ?? 0));
  const asIfUtc = Date.UTC(year, month - 1, day, hour, minute, second);
  // Date.UTC rolls 30 February over into March and hour 25 into the next day: only accept what comes back unchanged.
  const check = new Date(asIfUtc);
  if (
    Number.isNaN(asIfUtc) ||
    check.getUTCFullYear() !== year ||
    check.getUTCMonth() !== month - 1 ||
    check.getUTCDate() !== day ||
    check.getUTCHours() !== hour ||
    check.getUTCMinutes() !== minute
  ) {
    return null;
  }
  // The offset depends on the instant we are looking for: guess with the offset
  // at that wall-clock time read as UTC, then correct with the offset at the guess.
  const guess = asIfUtc - offsetAt(new Date(asIfUtc), zone);
  return new Date(asIfUtc - offsetAt(new Date(guess), zone));
}

/** The instants at which a calendar day starts and ends on the zone's clock. */
export function dayBounds(day: DayKey, zone: string): { from: Date; to: Date } | null {
  const from = fromLocalDateTime(`${day}T00:00`, zone);
  const next = addDays(day, 1);
  const to = next ? fromLocalDateTime(`${next}T00:00`, zone) : null;
  return from && to ? { from, to } : null;
}

/** The calendar day a number of days after (or before) the given one. */
export function addDays(day: DayKey, days: number): DayKey | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day);
  if (!match) {
    return null;
  }
  const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]) + days));
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
}

/** "20:45" */
export function formatTime(instant: string | Date, zone: string): string {
  return new Intl.DateTimeFormat(LOCALE, {
    timeZone: zone,
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).format(new Date(instant));
}

/** "Wed 15 Jan" */
export function formatDay(instant: string | Date, zone: string): string {
  return new Intl.DateTimeFormat(LOCALE, { timeZone: zone, weekday: "short", day: "numeric", month: "short" }).format(
    new Date(instant),
  );
}

/** "Wed 15 Jan 2030, 20:45" */
export function formatDateTime(instant: string | Date, zone: string): string {
  const year = dayKey(instant, zone).slice(0, 4);
  return `${formatDay(instant, zone)} ${year}, ${formatTime(instant, zone)}`;
}

/** A calendar day as a label: "Wed 15 Jan". */
export function formatDayKey(day: DayKey): string {
  // Noon UTC is the same calendar day in UTC whatever the day is.
  return new Intl.DateTimeFormat(LOCALE, { timeZone: "UTC", weekday: "short", day: "numeric", month: "short" }).format(
    new Date(`${day}T12:00:00Z`),
  );
}

/** "Today", "Tomorrow", or the weekday and date. */
export function relativeDayLabel(day: DayKey, today: DayKey): string {
  if (day === today) {
    return "Today";
  }
  if (day === addDays(today, 1)) {
    return "Tomorrow";
  }
  return formatDayKey(day);
}

/** 169 → "2 h 49 min" */
export function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) {
    return `${rest} min`;
  }
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
}

/** Seconds as a countdown: 581 → "9:41". Never negative. */
export function formatCountdown(totalSeconds: number): string {
  const seconds = Math.max(0, Math.ceil(totalSeconds));
  return `${Math.floor(seconds / 60)}:${pad(seconds % 60)}`;
}
