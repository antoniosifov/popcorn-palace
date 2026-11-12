"use server";

import { headers } from "next/headers";
import { redirect } from "next/navigation";

import { api } from "@/lib/api/client";
import type { AuthResponse } from "@/lib/api/types";
import { safeNextPath } from "@/lib/paths";
import type { ActionError } from "@/lib/errors";
import { endSession, sessionToken, startSession } from "@/lib/session";

import { describeFailure, text, UNAUTHENTICATED } from "./support";

/** What the login and registration forms get back when the attempt failed. */
export type AuthFormState = {
  error?: ActionError;
  /** What was typed, so the form is not emptied by a mistake. Never the password. */
  values?: { email?: string; displayName?: string };
};

/**
 * The API limits login attempts per client address. It sees this server, not
 * the visitor, so the visitor's address is passed on in the header a reverse
 * proxy would set.
 */
async function forwardedFor(): Promise<Record<string, string | undefined>> {
  return { "X-Forwarded-For": (await headers()).get("x-forwarded-for") ?? undefined };
}

export async function login(_previous: AuthFormState, form: FormData): Promise<AuthFormState> {
  const email = text(form, "email");
  const password = String(form.get("password") ?? "");
  let auth: AuthResponse;
  try {
    auth = await api<AuthResponse>("/auth/login", {
      method: "POST",
      body: { email, password },
      headers: await forwardedFor(),
    });
  } catch (error) {
    return { error: await describeFailure(error), values: { email } };
  }
  await startSession(auth);
  redirect(safeNextPath(text(form, "next")));
}

export async function register(_previous: AuthFormState, form: FormData): Promise<AuthFormState> {
  const email = text(form, "email");
  const displayName = text(form, "displayName");
  const password = String(form.get("password") ?? "");
  let auth: AuthResponse;
  try {
    auth = await api<AuthResponse>("/auth/register", {
      method: "POST",
      body: { email, password, displayName },
      headers: await forwardedFor(),
    });
  } catch (error) {
    return { error: await describeFailure(error), values: { email, displayName } };
  }
  await startSession(auth);
  redirect(safeNextPath(text(form, "next")));
}

/** What the form that asks for a reset link gets back. */
export type ResetRequestState = {
  error?: ActionError;
  /** The email the link was asked for, once the API has taken the request. */
  sentTo?: string;
  values?: { email?: string };
};

/**
 * Asks for a link to set a new password. The API answers the same whether or
 * not the email has an account, and so does this: success only means the
 * request was taken.
 */
export async function requestPasswordReset(_previous: ResetRequestState, form: FormData): Promise<ResetRequestState> {
  const email = text(form, "email");
  try {
    await api<void>("/auth/password-reset", {
      method: "POST",
      body: { email },
      headers: await forwardedFor(),
    });
  } catch (error) {
    return { error: await describeFailure(error), values: { email } };
  }
  return { sentTo: email };
}

/** What the form that sets the new password gets back when it did not work. */
export type NewPasswordState = { error?: ActionError };

export async function setNewPassword(_previous: NewPasswordState, form: FormData): Promise<NewPasswordState> {
  const token = text(form, "token");
  const password = String(form.get("password") ?? "");
  try {
    await api<void>("/auth/password-reset/confirm", {
      method: "POST",
      body: { token, password },
      headers: await forwardedFor(),
    });
  } catch (error) {
    return { error: await describeFailure(error) };
  }
  // The API has ended every session of the account; this browser's is one of them, if it had one.
  await endSession();
  redirect("/login?reset=done");
}

/** What the form that deletes the account gets back when it did not happen. */
export type DeleteAccountState = { error?: ActionError };

/**
 * Deletes the account of whoever is logged in, for good. The API asks for the
 * password of an account that has one; one opened with Google sends none.
 */
export async function deleteAccount(_previous: DeleteAccountState, form: FormData): Promise<DeleteAccountState> {
  const token = await sessionToken();
  if (!token) {
    return { error: UNAUTHENTICATED };
  }
  const password = String(form.get("password") ?? "");
  try {
    await api<void>("/users/me/delete", {
      method: "POST",
      token,
      body: password ? { password } : {},
      // Wrong passwords here count against the same limit as wrong passwords at login.
      headers: await forwardedFor(),
    });
  } catch (error) {
    return { error: await describeFailure(error) };
  }
  // The API has ended every session of the account; this is the cookie of one of them.
  await endSession();
  redirect("/goodbye");
}

export async function logout(): Promise<void> {
  // The token is stateless: forgetting it is all there is to logging out.
  await endSession();
  redirect("/");
}
