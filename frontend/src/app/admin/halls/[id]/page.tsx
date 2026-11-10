import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { HallPreview } from "@/components/hall-preview";
import { TitleRow } from "@/components/ui";
import { getHall } from "@/lib/api/catalog";
import { ApiError } from "@/lib/api/client";
import type { Hall } from "@/lib/api/types";

export default function HallPage({ params }: PageProps<"/admin/halls/[id]">) {
  return (
    <Suspense fallback={<div className="skeleton h-[360px] rounded-card" />}>
      <HallLayout params={params} />
    </Suspense>
  );
}

async function HallLayout({ params }: Pick<PageProps<"/admin/halls/[id]">, "params">) {
  const { id } = await params;
  const hallId = Number(id);
  if (!Number.isSafeInteger(hallId) || hallId <= 0) {
    notFound();
  }
  let hall: Hall;
  try {
    hall = await getHall(hallId);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
    throw error;
  }
  const vip = hall.rows.filter((row) => row.category === "VIP").reduce((total, row) => total + row.seats, 0);
  return (
    <>
      <TitleRow
        title={hall.name}
        sub={`${hall.rowCount} rows, ${hall.seatCount} seats: ${hall.seatCount - vip} standard and ${vip} VIP.`}
        action={
          <Link href="/admin/halls" className="btn btn-secondary">
            All halls
          </Link>
        }
      />
      <section className="card" aria-label="Layout">
        <HallPreview rows={hall.rows} />
      </section>
    </>
  );
}
