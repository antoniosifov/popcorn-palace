import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Suspense } from "react";

import { AuthForm } from "@/components/auth-form";
import { currentUser } from "@/lib/auth";
import { safeNextPath } from "@/lib/paths";

export const metadata: Metadata = { title: "Create account" };

export default function RegisterPage({ searchParams }: PageProps<"/register">) {
  return (
    <div className="column page-top">
      <div className="mx-auto w-full max-w-[440px] md:pt-8">
        <h1 className="display display-head">Create account</h1>
        <p className="mb-7 mt-3 text-muted">It takes a name, an email and a password.</p>
        <div className="card">
          <Suspense fallback={<div className="skeleton h-[230px]" />}>
            <Form searchParams={searchParams} />
          </Suspense>
        </div>
      </div>
    </div>
  );
}

async function Form({ searchParams }: Pick<PageProps<"/register">, "searchParams">) {
  const { next } = await searchParams;
  const destination = safeNextPath(typeof next === "string" ? next : undefined);
  // Somebody who is logged in already has nothing to do here.
  if (await currentUser()) {
    redirect(destination);
  }
  return <AuthForm mode="register" next={destination} />;
}
