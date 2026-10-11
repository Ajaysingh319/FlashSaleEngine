import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Skeleton } from "../ui/Skeleton";

/** Shared frame for the signed-in user's lists (orders, reservations). */
export function AccountPage({ icon: Icon, title, subtitle, children }: {
  icon: LucideIcon;
  title: string;
  subtitle: string;
  children: ReactNode;
}) {
  return (
    <section className="mx-auto max-w-4xl px-4 py-10 sm:px-6">
      <div className="mb-8 flex items-start gap-4">
        <span className="grid size-12 shrink-0 place-items-center rounded-2xl bg-brand-soft text-brand">
          <Icon className="size-6" aria-hidden />
        </span>
        <div>
          <h1 className="text-3xl font-extrabold">{title}</h1>
          <p className="mt-1 text-sm text-ink-muted">{subtitle}</p>
        </div>
      </div>
      {children}
    </section>
  );
}

export function AccountListSkeleton() {
  return (
    <div className="space-y-4" aria-label="Loading">
      {[0, 1, 2].map((row) => <Skeleton key={row} className="h-32 rounded-3xl" />)}
    </div>
  );
}

export function EmptyAccountList({ title, message }: { title: string; message: string }) {
  return (
    <div className="rounded-3xl border border-dashed border-line bg-white px-6 py-14 text-center">
      <p className="font-semibold">{title}</p>
      <p className="mx-auto mt-1 max-w-md text-sm text-ink-muted">{message}</p>
      <Link to="/sales" className="mt-6 inline-block rounded-full bg-brand px-6 py-3 text-sm font-bold text-white transition hover:bg-brand-deep">
        Browse flash sales
      </Link>
    </div>
  );
}
