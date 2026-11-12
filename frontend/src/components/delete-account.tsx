"use client";

import { useActionState, useState } from "react";

import { deleteAccount, type DeleteAccountState } from "@/actions/auth";

import { ErrorNotice, Field, fieldProps } from "./ui";

const HINT = "To confirm it is you.";

/**
 * Deleting an account asks once more, in place, like every action that cannot
 * be undone. An account with a password is also asked for it: a browser left
 * open must not be enough. One opened with Google has no password to ask for.
 */
export function DeleteAccount({ hasPassword }: { hasPassword: boolean }) {
  const [asking, setAsking] = useState(false);
  const [state, action, pending] = useActionState<DeleteAccountState, FormData>(deleteAccount, {});
  const errors = state.error?.fieldErrors ?? {};

  if (!asking) {
    return (
      <button type="button" className="btn btn-danger" onClick={() => setAsking(true)}>
        Delete account
      </button>
    );
  }

  return (
    <form action={action} className="grid gap-4">
      <ErrorNotice error={state.error} />

      {hasPassword ? (
        <Field label="Your password" name="password" error={errors.password} hint={HINT}>
          <input
            {...fieldProps("password", errors.password, HINT)}
            className="input"
            type="password"
            autoComplete="current-password"
            maxLength={72}
            required
          />
        </Field>
      ) : null}

      <p className="text-muted">Delete this account for good? It cannot be brought back.</p>
      <div className="flex flex-wrap gap-2">
        <button type="submit" className="btn btn-danger" disabled={pending}>
          {pending ? "Working…" : "Delete for good"}
        </button>
        <button type="button" className="btn btn-secondary" disabled={pending} onClick={() => setAsking(false)}>
          Keep it
        </button>
      </div>
    </form>
  );
}
