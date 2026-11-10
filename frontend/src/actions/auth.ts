"use server";

import { headers } from "next/headers";
import { redirect } from "next/navigation";

import { api } from "@/lib/api/client";
import type { AuthResponse } from "@/lib/api/types";
import { safeNextPath } from "@/lib/paths";
import type { ActionError } from "@/lib/errors";
import { endSession, startSession } from "@/lib/session";

import { describeFailure, text } from "./support";

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

export async function logout(): Promise<void> {
  // The token is stateless: forgetting it is all there is to logging out.
  await endSession();
  redirect("/");
}
