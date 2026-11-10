import type { Metadata } from "next";
import { Suspense } from "react";

import { AdminTabs } from "@/components/admin-tabs";
import { requireAdmin } from "@/lib/auth";

export const metadata: Metadata = { title: "Admin" };

/**
 * Nothing of the back office is rendered for anybody who is not an
 * administrator. This keeps the pages away from customers; the API refuses
 * their requests on its own, whatever the pages do.
 */
async function AdministratorsOnly({ children }: { children: React.ReactNode }) {
  await requireAdmin("/admin");
  return children;
}

export default function AdminLayout({ children }: LayoutProps<"/admin">) {
  return (
    <Suspense fallback={<div className="skeleton h-[320px] rounded-card" />}>
      <AdministratorsOnly>
        <AdminTabs />
        {children}
      </AdministratorsOnly>
    </Suspense>
  );
}
