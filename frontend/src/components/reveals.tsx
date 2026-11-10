"use client";

import { useEffect } from "react";

const FROM = { opacity: 0, transform: "translateY(24px)" };
const TO = { opacity: 1, transform: "none" };

/**
 * Parts of a page marked `rise` come up into place the first time they are
 * scrolled to, once.
 *
 * Each part below the screen gets an animation that is held on its first
 * frame, and let go when the part comes into view. Nothing is written into
 * the page to do it (no class, no attribute, no style), so React finds the
 * markup exactly as the server sent it; and without scripts, or for a reader
 * who asked for less motion, every part is simply on the page.
 */
export function Reveals() {
  useEffect(() => {
    if (!("IntersectionObserver" in window) || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      return;
    }
    const waiting = new WeakMap<Element, Animation>();

    const seen = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            waiting.get(entry.target)?.play();
            waiting.delete(entry.target);
            seen.unobserve(entry.target);
          }
        }
      },
      { rootMargin: "0px 0px -8% 0px", threshold: 0.05 },
    );

    const hold = (part: Element) => {
      if (waiting.has(part)) {
        return;
      }
      const animation = part.animate([FROM, TO], {
        duration: 800,
        easing: "cubic-bezier(0.16, 1, 0.3, 1)",
        fill: "backwards",
      });
      animation.pause();
      waiting.set(part, animation);
      seen.observe(part);
    };

    // What is on the screen already stays where it is: nothing a reader is looking at blinks out and back.
    for (const part of document.querySelectorAll(".rise")) {
      const box = part.getBoundingClientRect();
      if (box.top >= window.innerHeight || box.bottom <= 0) {
        hold(part);
      }
    }

    // Content that streams in later, or arrives with a change of page, is held as it is added.
    const added = new MutationObserver((records) => {
      for (const record of records) {
        for (const node of record.addedNodes) {
          if (node instanceof Element) {
            if (node.matches(".rise")) {
              hold(node);
            }
            node.querySelectorAll(".rise").forEach(hold);
          }
        }
      }
    });
    added.observe(document.body, { childList: true, subtree: true });

    return () => {
      seen.disconnect();
      added.disconnect();
      for (const part of document.querySelectorAll(".rise")) {
        waiting.get(part)?.cancel();
      }
    };
  }, []);

  return null;
}
