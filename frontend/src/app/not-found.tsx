import Link from "next/link";

export default function NotFound() {
  return (
    <div className="column page-top">
      <div className="max-w-[520px] md:pt-8">
        <h1 className="display display-head">Nothing here</h1>
        <p className="mb-7 mt-4 text-section">This page does not exist, or it is not yours to see.</p>
        <Link href="/" className="btn btn-primary">
          See what is showing
        </Link>
      </div>
    </div>
  );
}
