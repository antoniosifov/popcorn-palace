"use client";

import Link from "next/link";
import { useActionState } from "react";

import { requestPasswordReset, setNewPassword, type NewPasswordState, type ResetRequestState } from "@/actions/auth";

import { ErrorNotice, Field, fieldProps, Notice } from "./ui";

/**
 * Asks for the email a reset link should go to. The answer is the same
 * whether or not the email has an account, in the API and in these words:
 * the form must not tell a stranger which addresses are registered.
 */
export function ForgotPasswordForm() {
  const [state, action, pending] = useActionState<ResetRequestState, FormData>(requestPasswordReset, {});
  const errors = state.error?.fieldErrors ?? {};

  if (state.sentTo) {
    return (
      <div className="grid gap-4">
        <Notice tone="success">
          If there is an account for {state.sentTo}, a link to set a new password is on its way to it.
        </Notice>
        <p className="text-muted">
          The link works once, for 30 minutes. If nothing arrives, look in the spam folder and check the address.
        </p>
        <p>
          <Link href="/login" className="link">
            Back to log in
          </Link>
        </p>
      </div>
    );
  }

  return (
    <form action={action} className="grid gap-4">
      <ErrorNotice error={state.error} />

      <Field label="Email" name="email" error={errors.email}>
        <input
          {...fieldProps("email", errors.email)}
          className="input"
          type="email"
          autoComplete="email"
          inputMode="email"
          maxLength={254}
          required
          defaultValue={state.values?.email}
        />
      </Field>

      <button type="submit" className="btn btn-primary btn-large mt-1" disabled={pending}>
        {pending ? "One moment…" : "Send the link"}
      </button>

      <p className="text-muted">
        Remembered it?{" "}
        <Link href="/login" className="link">
          Log in
        </Link>
      </p>
    </form>
  );
}

/** Sets the new password with the token the link carried. */
export function NewPasswordForm({ token }: { token: string }) {
  const [state, action, pending] = useActionState<NewPasswordState, FormData>(setNewPassword, {});
  const errors = state.error?.fieldErrors ?? {};
  const spent = state.error?.code === "RESET_LINK_INVALID";

  return (
    <form action={action} className="grid gap-4">
      <input type="hidden" name="token" value={token} />
      <ErrorNotice error={state.error} />

      {spent ? (
        <p>
          <Link href="/forgot-password" className="link">
            Ask for a new link
          </Link>
        </p>
      ) : (
        <>
          <Field label="New password" name="password" error={errors.password} hint="8 to 72 characters.">
            <input
              {...fieldProps("password", errors.password, "8 to 72 characters.")}
              className="input"
              type="password"
              autoComplete="new-password"
              minLength={8}
              maxLength={72}
              required
            />
          </Field>

          <button type="submit" className="btn btn-primary btn-large mt-1" disabled={pending}>
            {pending ? "One moment…" : "Set new password"}
          </button>
        </>
      )}
    </form>
  );
}
