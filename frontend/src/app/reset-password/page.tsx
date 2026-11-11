import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";

import { NewPasswordForm } from "@/components/password-forms";
import { Notice } from "@/components/ui";

/**
 * The address of this page carries the token, which is a secret. Nothing the
 * page loads from elsewhere is told that address, and no search engine is
 * invited to keep it.
 */
export const metadata: Metadata = {
  title: "Set a new password",
  referrer: "no-referrer",
  robots: { index: false, follow: false },
};

export default function ResetPasswordPage({ searchParams }: PageProps<"/reset-password">) {
  return (
    <div className="column page-top">
      <div className="mx-auto w-full max-w-[440px] md:pt-8">
        <h1 className="display display-head">Set a new password</h1>
        <p className="mb-7 mt-3 text-muted">Choose the password you will log in with from now on.</p>
        <div className="card">
          <Suspense fallback={<div className="skeleton h-[150px]" />}>
            <Form searchParams={searchParams} />
          </Suspense>
        </div>
      </div>
    </div>
  );
}

async function Form({ searchParams }: Pick<PageProps<"/reset-password">, "searchParams">) {
  const { token } = await searchParams;
  // Whether the token is any good is for the API to say, when the form is sent. Here it only has to be there.
  if (typeof token !== "string" || token.length === 0 || token.length > 200) {
    return (
      <div className="grid gap-4">
        <Notice tone="error">This link is not complete. Open it from the email again, or ask for a new one.</Notice>
        <p>
          <Link href="/forgot-password" className="link">
            Ask for a new link
          </Link>
        </p>
      </div>
    );
  }
  return <NewPasswordForm token={token} />;
}
