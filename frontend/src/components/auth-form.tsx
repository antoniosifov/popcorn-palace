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
  problem,
  google = false,
}: {
  mode: "login" | "register";
  next: string;
  /** Good news from where the visitor came from, such as a password that was just changed. */
  notice?: string;
  /** Bad news from where the visitor came from: a sign-in with Google that did not work. */
  problem?: string;
  /** Whether signing in with Google is set up here; the button is offered only then. */
  google?: boolean;
}) {
  const registering = mode === "register";
  const [state, action, pending] = useActionState<AuthFormState, FormData>(registering ? register : login, {});
  const errors = state.error?.fieldErrors ?? {};
  const other = `${registering ? "/login" : "/register"}${next === "/" ? "" : `?next=${encodeURIComponent(next)}`}`;

  return (
    <form action={action} className="grid gap-4">
      <input type="hidden" name="next" value={next} />
      {notice && !state.error ? <Notice tone="success">{notice}</Notice> : null}
      {problem && !state.error ? <Notice tone="error">{problem}</Notice> : null}
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

      {google ? (
        <>
          <p className="flex items-center gap-3 text-label text-faint" aria-hidden="true">
            <span className="h-px flex-1 bg-rule" />
            or
            <span className="h-px flex-1 bg-rule" />
          </p>
          {/* A plain link, not a client-side navigation: it leaves this site for Google. */}
          <a
            href={`/api/auth/google${next === "/" ? "" : `?next=${encodeURIComponent(next)}`}`}
            className="btn btn-secondary btn-large"
          >
            <GoogleMark />
            Continue with Google
          </a>
        </>
      ) : null}

      <p className="text-muted">
        {registering ? "Already have an account?" : "New here?"}{" "}
        <Link href={other} className="link">
          {registering ? "Log in" : "Create an account"}
        </Link>
      </p>
    </form>
  );
}

/** Google's mark, in its own colours, as its sign-in branding asks. */
function GoogleMark() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true" focusable="false">
      <path
        fill="#4285F4"
        d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.7-1.57 2.68-3.88 2.68-6.62z"
      />
      <path
        fill="#34A853"
        d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.8.54-1.84.86-3.04.86-2.34 0-4.32-1.58-5.03-3.7H.96v2.33A9 9 0 0 0 9 18z"
      />
      <path fill="#FBBC05" d="M3.97 10.72a5.4 5.4 0 0 1 0-3.44V4.95H.96a9 9 0 0 0 0 8.1l3.01-2.33z" />
      <path
        fill="#EA4335"
        d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.59C13.46.89 11.43 0 9 0A9 9 0 0 0 .96 4.95l3.01 2.33C4.68 5.16 6.66 3.58 9 3.58z"
      />
    </svg>
  );
}
