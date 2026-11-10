import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Suspense } from "react";

import { AuthForm } from "@/components/auth-form";
import { currentUser } from "@/lib/auth";
import { safeNextPath } from "@/lib/paths";

export const metadata: Metadata = { title: "Log in" };

export default function LoginPage({ searchParams }: PageProps<"/login">) {
  return (
    <div className="mx-auto w-full max-w-[420px] pt-6 md:pt-12">
      <h1 className="text-title font-semibold tracking-[-0.01em]">Log in</h1>
      <p className="mb-5 mt-1 text-muted">To book seats and see your tickets.</p>
      <div className="card">
        <Suspense fallback={<div className="skeleton h-[230px]" />}>
          <Form searchParams={searchParams} />
        </Suspense>
      </div>
    </div>
  );
}

async function Form({ searchParams }: Pick<PageProps<"/login">, "searchParams">) {
  const { next } = await searchParams;
  const destination = safeNextPath(typeof next === "string" ? next : undefined);
  // Somebody who is logged in already has nothing to do here.
  if (await currentUser()) {
    redirect(destination);
  }
  return <AuthForm mode="login" next={destination} />;
}
