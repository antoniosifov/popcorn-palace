import { describe, expect, it } from "vitest";

import {
  addDays,
  dayBounds,
  dayKey,
  formatCountdown,
  formatDateTime,
  formatDay,
  formatDuration,
  formatTime,
  fromLocalDateTime,
  isValidZone,
  relativeDayLabel,
  toLocalDateTime,
} from "./time";

const BERLIN = "Europe/Berlin";
const NEW_YORK = "America/New_York";

describe("times on the cinema's clock", () => {
  it("shows an instant as the wall-clock time of the zone, not of the server", () => {
    // 19:45 UTC in January is 20:45 in Berlin and 14:45 in New York.
    const instant = "2030-01-15T19:45:00Z";
    expect(formatTime(instant, BERLIN)).toBe("20:45");
    expect(formatTime(instant, NEW_YORK)).toBe("14:45");
    expect(formatTime(instant, "UTC")).toBe("19:45");
  });

  it("follows daylight saving: the same clock time is a different offset in summer", () => {
    expect(formatTime("2030-07-15T18:45:00Z", BERLIN)).toBe("20:45");
  });

  it("puts a late showtime on the right calendar day of the zone", () => {
    // 23:30 UTC on the 15th is already the 16th in Berlin, and still the 15th in New York.
    const instant = "2030-01-15T23:30:00Z";
    expect(dayKey(instant, BERLIN)).toBe("2030-01-16");
    expect(dayKey(instant, NEW_YORK)).toBe("2030-01-15");
    expect(formatDay(instant, BERLIN)).toBe("Wed 16 Jan");
  });

  it("formats a date with its time", () => {
    expect(formatDateTime("2030-01-15T19:45:00Z", BERLIN)).toBe("Tue 15 Jan 2030, 20:45");
  });
});

describe("what an administrator types into a date and time field", () => {
  it("is read on the cinema's clock", () => {
    expect(fromLocalDateTime("2030-01-15T20:45", BERLIN)?.toISOString()).toBe("2030-01-15T19:45:00.000Z");
    expect(fromLocalDateTime("2030-07-15T20:45", BERLIN)?.toISOString()).toBe("2030-07-15T18:45:00.000Z");
    expect(fromLocalDateTime("2030-01-15T20:45", NEW_YORK)?.toISOString()).toBe("2030-01-16T01:45:00.000Z");
    expect(fromLocalDateTime("2030-01-15T20:45", "UTC")?.toISOString()).toBe("2030-01-15T20:45:00.000Z");
  });

  it("comes back as the same text when shown again", () => {
    for (const zone of [BERLIN, NEW_YORK, "Asia/Kolkata", "Pacific/Auckland", "UTC"]) {
      for (const local of ["2030-01-15T00:00", "2030-03-30T23:59", "2030-07-01T12:30", "2030-10-27T10:00"]) {
        const instant = fromLocalDateTime(local, zone);
        expect(instant, `${local} in ${zone}`).not.toBeNull();
        expect(toLocalDateTime(instant as Date, zone)).toBe(local);
      }
    }
  });

  it("moves a time that the start of daylight saving skips to the same moment on the new clock", () => {
    // In Berlin the clocks jump from 02:00 to 03:00 on 31 March 2030: 02:30 does not exist.
    const instant = fromLocalDateTime("2030-03-31T02:30", BERLIN) as Date;
    expect(instant.toISOString()).toBe("2030-03-31T01:30:00.000Z");
    expect(toLocalDateTime(instant, BERLIN)).toBe("2030-03-31T03:30");
  });

  it("resolves a time that happens twice to one of the two, on the right clock time", () => {
    // 27 October 2030, Berlin: 02:30 happens at 00:30 UTC and again at 01:30 UTC.
    const instant = fromLocalDateTime("2030-10-27T02:30", BERLIN) as Date;
    expect(["2030-10-27T00:30:00.000Z", "2030-10-27T01:30:00.000Z"]).toContain(instant.toISOString());
    expect(toLocalDateTime(instant, BERLIN)).toBe("2030-10-27T02:30");
  });

  it("refuses what is not a date and time", () => {
    for (const text of ["", "tomorrow", "2030-01-15", "2030-02-30T10:00", "2030-01-15T25:00", "2030-13-01T10:00"]) {
      expect(fromLocalDateTime(text, BERLIN), text).toBeNull();
    }
  });
});

describe("calendar days", () => {
  it("start and end at midnight on the cinema's clock", () => {
    const bounds = dayBounds("2030-01-15", BERLIN);
    expect(bounds?.from.toISOString()).toBe("2030-01-14T23:00:00.000Z");
    expect(bounds?.to.toISOString()).toBe("2030-01-15T23:00:00.000Z");
  });

  it("are 23 hours long on the night the clocks go forward", () => {
    const bounds = dayBounds("2030-03-31", BERLIN);
    const hours = ((bounds?.to.getTime() ?? 0) - (bounds?.from.getTime() ?? 0)) / 3_600_000;
    expect(hours).toBe(23);
  });

  it("add up across months and years", () => {
    expect(addDays("2030-01-31", 1)).toBe("2030-02-01");
    expect(addDays("2030-12-31", 1)).toBe("2031-01-01");
    expect(addDays("2030-03-01", -1)).toBe("2030-02-28");
    expect(addDays("not a day", 1)).toBeNull();
  });

  it("are called today and tomorrow when they are", () => {
    expect(relativeDayLabel("2030-01-15", "2030-01-15")).toBe("Today");
    expect(relativeDayLabel("2030-01-16", "2030-01-15")).toBe("Tomorrow");
    expect(relativeDayLabel("2030-01-17", "2030-01-15")).toBe("Thu 17 Jan");
  });
});

describe("small formats", () => {
  it("writes a length in hours and minutes", () => {
    expect(formatDuration(169)).toBe("2 h 49 min");
    expect(formatDuration(120)).toBe("2 h");
    expect(formatDuration(45)).toBe("45 min");
  });

  it("writes a countdown that never goes below zero", () => {
    expect(formatCountdown(600)).toBe("10:00");
    expect(formatCountdown(581)).toBe("9:41");
    expect(formatCountdown(9)).toBe("0:09");
    expect(formatCountdown(-5)).toBe("0:00");
  });

  it("knows a time zone name from a typo", () => {
    expect(isValidZone("Europe/Berlin")).toBe(true);
    expect(isValidZone("Europe/Berlinn")).toBe(false);
  });
});
