import Link from "next/link";

export default function NotFound() {
  return (
    <div className="mx-auto w-full max-w-[420px] pt-6 md:pt-12">
      <h1 className="text-title font-semibold tracking-[-0.01em]">Nothing here</h1>
      <p className="mb-5 mt-1 text-muted">This page does not exist, or it is not yours to see.</p>
      <Link href="/" className="btn btn-primary">
        See what is showing
      </Link>
    </div>
  );
}
