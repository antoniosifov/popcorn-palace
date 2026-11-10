import type { ReactNode } from "react";

import type { ActionError } from "@/lib/errors";

/** The title of a screen: a noun at 20px, an optional sentence about the data, and the screen's action. */
export function TitleRow({ title, sub, action }: { title: string; sub?: ReactNode; action?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-x-6 gap-y-3">
      <div className="min-w-0">
        <h1 className="text-title font-semibold tracking-[-0.01em]">{title}</h1>
        {sub ? <p className="mt-1 text-muted">{sub}</p> : null}
      </div>
      {action}
    </div>
  );
}

/** A message inside the block it is about. An error names the request, so it can be found in the logs. */
export function Notice({
  tone = "info",
  children,
  traceId,
}: {
  tone?: "info" | "error" | "success";
  children: ReactNode;
  traceId?: string;
}) {
  const colours =
    tone === "error"
      ? "bg-negative-tint text-negative"
      : tone === "success"
        ? "bg-positive-tint text-positive"
        : "bg-well text-ink";
  return (
    <div role={tone === "error" ? "alert" : "status"} className={`rounded-inner px-3.5 py-2.5 ${colours}`}>
      <p>{children}</p>
      {traceId ? <p className="num mt-1 text-small text-muted">Reference {traceId}</p> : null}
    </div>
  );
}

export function ErrorNotice({ error }: { error?: ActionError | null }) {
  if (!error) {
    return null;
  }
  return (
    <Notice tone="error" traceId={error.traceId}>
      {error.message}
    </Notice>
  );
}

/** An empty block keeps its frame and says, in one sentence, what would be here. */
export function Empty({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-inner border border-dashed border-control px-4 py-6 text-center text-muted">{children}</div>
  );
}

/** A labelled form field with the API's complaint about it underneath. */
export function Field({
  label,
  name,
  error,
  hint,
  children,
}: {
  label: string;
  name: string;
  error?: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <div className="grid gap-2">
      <label htmlFor={name} className="font-medium">
        {label}
      </label>
      {children}
      {error ? (
        <p id={`${name}-error`} className="text-label text-negative">
          {error}
        </p>
      ) : hint ? (
        <p id={`${name}-hint`} className="text-label text-muted">
          {hint}
        </p>
      ) : null}
    </div>
  );
}

/** What an input needs to be tied to its {@link Field}. */
export function fieldProps(name: string, error?: string, hint?: string) {
  return {
    id: name,
    name,
    "aria-invalid": error ? (true as const) : undefined,
    "aria-describedby": error ? `${name}-error` : hint ? `${name}-hint` : undefined,
  };
}
