"use client";

import { useSyncExternalStore } from "react";

/**
 * The current second, shared by every countdown on the page: one timer,
 * however many components watch it. On the server (and until the page has
 * hydrated) there is no "now", so a countdown shows a dash instead of a time
 * that would differ between the server's HTML and the browser's first render.
 */

const listeners = new Set<() => void>();
let timer: ReturnType<typeof setInterval> | undefined;

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  timer ??= setInterval(() => listeners.forEach((notify) => notify()), 1000);
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0 && timer !== undefined) {
      clearInterval(timer);
      timer = undefined;
    }
  };
}

const currentSecond = () => Math.floor(Date.now() / 1000);
const noClock = () => null;

/** Seconds since the epoch, ticking once a second; null before hydration. */
export function useNowSeconds(): number | null {
  return useSyncExternalStore(subscribe, currentSecond, noClock);
}
