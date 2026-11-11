import type { Metadata } from "next";

import { ForgotPasswordForm } from "@/components/password-forms";

export const metadata: Metadata = { title: "Forgot your password?" };

export default function ForgotPasswordPage() {
  return (
    <div className="column page-top">
      <div className="mx-auto w-full max-w-[440px] md:pt-8">
        <h1 className="display display-head">Forgot your password?</h1>
        <p className="mb-7 mt-3 text-muted">
          Type the email of your account, and a link to set a new one is sent to it.
        </p>
        <div className="card">
          <ForgotPasswordForm />
        </div>
      </div>
    </div>
  );
}
