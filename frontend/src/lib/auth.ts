import "server-only";

import { cache } from "react";
import { notFound, redirect } from "next/navigation";

import { api, ApiError } from "./api/client";
import type { User } from "./api/types";
import { loginPath } from "./paths";
import { sessionToken } from "./session";

/**
 * Who is making this request, according to the API. Asked once per request
 * however many components need it. A missing, expired or forged token is
 * simply "nobody": the API is the one that verifies it.
 */
export const currentUser = cache(async (): Promise<User | null> => {
  const token = await sessionToken();
  if (!token) {
    return null;
  }
  try {
    return await api<User>("/users/me", { token });
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null;
    }
    throw error;
  }
});

/** The logged-in user, or a redirect to the login page that returns here afterwards. */
export async function requireUser(next: string): Promise<User> {
  const user = await currentUser();
  if (!user) {
    redirect(loginPath(next));
  }
  return user;
}

/**
 * The logged-in administrator. Somebody who is not one gets "not found", the
 * same answer as for a page that does not exist: no hint that there is an
 * admin area. This guards the pages; the API guards the data on its own.
 */
export async function requireAdmin(next: string): Promise<User> {
  const user = await requireUser(next);
  if (user.role !== "ADMIN") {
    notFound();
  }
  return user;
}
