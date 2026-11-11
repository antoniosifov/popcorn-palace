import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Suspense } from "react";

import { AuthForm } from "@/components/auth-form";
import { currentUser } from "@/lib/auth";
import { googleProblem } from "@/lib/errors";
import { googleSettings } from "@/lib/google";
import { safeNextPath } from "@/lib/paths";

export const metadata: Metadata = { title: "Log in" };

export default function LoginPage({ searchParams }: PageProps<"/login">) {
  return (
    <div className="column page-top">
      <div className="mx-auto w-full max-w-[440px] md:pt-8">
        <h1 className="display display-head">Log in</h1>
        <p className="mb-7 mt-3 text-muted">To book seats and see your tickets.</p>
        <div className="card">
          <Suspense fallback={<div className="skeleton h-[230px]" />}>
            <Form searchParams={searchParams} />
          </Suspense>
        </div>
      </div>
    </div>
  );
}

async function Form({ searchParams }: Pick<PageProps<"/login">, "searchParams">) {
  const { next, reset, google } = await searchParams;
  const destination = safeNextPath(typeof next === "string" ? next : undefined);
  // Somebody who is logged in already has nothing to do here.
  if (await currentUser()) {
    redirect(destination);
  }
  // Arriving from the page that set a new password.
  const notice = reset === "done" ? "Your password has been changed. Log in with the new one." : undefined;
  // Arriving from a sign-in with Google that did not work.
  const problem = typeof google === "string" ? googleProblem(google) : undefined;
  return (
    <AuthForm mode="login" next={destination} notice={notice} problem={problem} google={googleSettings() !== null} />
  );
}
