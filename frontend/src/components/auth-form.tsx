"use client";

import Link from "next/link";
import { useActionState } from "react";

import { login, register, type AuthFormState } from "@/actions/auth";

import { ErrorNotice, Field, fieldProps, Notice } from "./ui";

/**
 * The login and the registration form: the same fields, one more for a new
 * account. They work without JavaScript too: the form posts to a Server Action.
 */
export function AuthForm({
  mode,
  next,
  notice,
}: {
  mode: "login" | "register";
  next: string;
  /** Good news from where the visitor came from, such as a password that was just changed. */
  notice?: string;
}) {
  const registering = mode === "register";
  const [state, action, pending] = useActionState<AuthFormState, FormData>(registering ? register : login, {});
  const errors = state.error?.fieldErrors ?? {};
  const other = `${registering ? "/login" : "/register"}${next === "/" ? "" : `?next=${encodeURIComponent(next)}`}`;

  return (
    <form action={action} className="grid gap-4">
      <input type="hidden" name="next" value={next} />
      {notice && !state.error ? <Notice tone="success">{notice}</Notice> : null}
      <ErrorNotice error={state.error} />

      {registering ? (
        <Field label="Name" name="displayName" error={errors.displayName} hint="Printed on your tickets.">
          <input
            {...fieldProps("displayName", errors.displayName, "Printed on your tickets.")}
            className="input"
            type="text"
            autoComplete="name"
            maxLength={100}
            required
            defaultValue={state.values?.displayName}
          />
        </Field>
      ) : null}

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

      <Field
        label="Password"
        name="password"
        error={errors.password}
        hint={registering ? "8 to 72 characters." : undefined}
      >
        <input
          {...fieldProps("password", errors.password, registering ? "8 to 72 characters." : undefined)}
          className="input"
          type="password"
          autoComplete={registering ? "new-password" : "current-password"}
          minLength={registering ? 8 : undefined}
          maxLength={72}
          required
        />
      </Field>

      {registering ? null : (
        <p className="-mt-1 justify-self-end text-label">
          <Link href="/forgot-password" className="link-quiet">
            Forgot your password?
          </Link>
        </p>
      )}

      <button type="submit" className="btn btn-primary btn-large mt-1" disabled={pending}>
        {pending ? "One moment…" : registering ? "Create account" : "Log in"}
      </button>

      <p className="text-muted">
        {registering ? "Already have an account?" : "New here?"}{" "}
        <Link href={other} className="link">
          {registering ? "Log in" : "Create an account"}
        </Link>
      </p>
    </form>
  );
}
