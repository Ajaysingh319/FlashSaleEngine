import { Link } from "react-router-dom";

export function NotFoundPage() {
  return (
    <section className="mx-auto flex max-w-xl flex-col items-center px-4 py-24 text-center">
      <p className="text-sm font-bold tracking-widest text-brand uppercase">Not available</p>
      <h1 className="mt-3 text-3xl font-extrabold">This page is not available yet</h1>
      <p className="mt-3 text-ink-muted">It may not exist, or it is still being built. The live flash sales are on the home page.</p>
      <Link to="/" className="mt-8 rounded-full bg-brand px-6 py-3 text-sm font-bold text-white shadow-lg shadow-brand/25 transition hover:bg-brand-deep">
        Back to flash sales
      </Link>
    </section>
  );
}
