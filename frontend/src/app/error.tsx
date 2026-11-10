"use client";

import { useEffect } from "react";

/**
 * Shown in place of a page that could not be drawn, most often because the
 * booking service did not answer. The frame of the site stays; trying again
 * fetches and renders the page afresh.
 */
export default function ErrorPage({ error, retry }: { error: unknown; retry: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  // In production the server sends a digest instead of the message: a reference to its own log line.
  const digest = typeof error === "object" && error !== null && "digest" in error ? String(error.digest) : null;

  return (
    <div className="column page-top">
      <div className="max-w-[520px] md:pt-8">
        <h1 className="display display-head">The box office is not answering</h1>
        <p className="mb-7 mt-4 text-section">
          We could not load this page. Nothing was booked or charged by this. Try again in a moment.
        </p>
        <button type="button" className="btn btn-primary" onClick={() => retry()}>
          Try again
        </button>
        {digest ? <p className="num mt-4 text-small text-faint">Reference {digest}</p> : null}
      </div>
    </div>
  );
}
