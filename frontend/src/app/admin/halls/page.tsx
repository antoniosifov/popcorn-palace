import Link from "next/link";
import { Suspense } from "react";

import { removeHall } from "@/actions/admin";
import { ConfirmButton } from "@/components/confirm-button";
import { Empty, TitleRow } from "@/components/ui";
import { listHalls } from "@/lib/api/catalog";

export default function AdminHallsPage() {
  return (
    <>
      <TitleRow
        title="Halls"
        sub="A hall keeps its layout for good: tickets point at its seats."
        action={
          <Link href="/admin/halls/new" className="btn btn-primary">
            Add a hall
          </Link>
        }
      />
      <section className="card max-w-[760px]">
        <Suspense fallback={<div className="skeleton h-[200px]" />}>
          <Halls />
        </Suspense>
      </section>
    </>
  );
}

async function Halls() {
  const halls = await listHalls();
  if (halls.length === 0) {
    return <Empty>There are no halls yet. Add the first one.</Empty>;
  }
  return (
    <table className="table">
      <thead>
        <tr>
          <th scope="col">Name</th>
          <th scope="col" className="n">
            Rows
          </th>
          <th scope="col" className="n">
            Seats
          </th>
          <th scope="col" className="n">
            <span className="sr-only">Actions</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {halls.map((hall) => (
          <tr key={hall.id}>
            <td className="font-medium">
              <Link href={`/admin/halls/${hall.id}`} className="hover:underline hover:underline-offset-4">
                {hall.name}
              </Link>
            </td>
            <td className="n">{hall.rowCount}</td>
            <td className="n">{hall.seatCount}</td>
            <td className="n whitespace-nowrap">
              <Link href={`/admin/halls/${hall.id}`} className="link mr-4">
                Layout
              </Link>
              <ConfirmButton
                look="text"
                label="Delete"
                question={`Delete ${hall.name} and its seats?`}
                confirmLabel="Delete hall"
                action={removeHall.bind(null, hall.id)}
              />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
