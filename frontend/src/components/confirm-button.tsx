"use client";

import { useState, useTransition } from "react";

import type { ActionResult } from "@/lib/errors";

import { ErrorNotice } from "./ui";

type Props = {
  /** The first press: names what will happen. */
  label: string;
  /** The question asked before it happens, with the consequence in words. */
  question: string;
  /** The second press. */
  confirmLabel: string;
  /** Runs when confirmed. A refusal is shown next to the button. */
  action: () => Promise<ActionResult<unknown>>;
  /** A row action in a table is plain text; a page action is an outlined button. */
  look?: "button" | "text";
};

/**
 * An action that cannot be undone asks once more, in place, before it runs:
 * no dialog over the page, just the question where the button was.
 */
export function ConfirmButton({ label, question, confirmLabel, action, look = "button" }: Props) {
  const [asking, setAsking] = useState(false);
  const [pending, startTransition] = useTransition();
  const [result, setResult] = useState<ActionResult<unknown> | null>(null);
  const error = result && !result.ok ? result.error : null;

  if (!asking) {
    return (
      <div className={look === "text" ? "inline-grid justify-items-end gap-2" : "grid gap-2.5"}>
        <button
          type="button"
          className={look === "text" ? "link-quiet hover:text-negative" : "btn btn-danger"}
          onClick={() => {
            setResult(null);
            setAsking(true);
          }}
        >
          {label}
        </button>
        <ErrorNotice error={error} />
      </div>
    );
  }

  return (
    <div className={look === "text" ? "inline-grid justify-items-end gap-2" : "grid gap-2.5"}>
      <p className={look === "text" ? "text-right text-label text-muted" : "text-muted"}>{question}</p>
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          className="btn btn-danger"
          disabled={pending}
          onClick={() =>
            startTransition(async () => {
              const outcome = await action();
              setResult(outcome);
              setAsking(false);
            })
          }
        >
          {pending ? "Working…" : confirmLabel}
        </button>
        <button type="button" className="btn btn-secondary" disabled={pending} onClick={() => setAsking(false)}>
          Keep it
        </button>
      </div>
    </div>
  );
}
