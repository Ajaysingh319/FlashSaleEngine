import { Link } from "react-router-dom";
import { Logo } from "./Header";

const COLUMNS = [
  { title: "Explore", links: [["Home", "/"], ["Flash Sales", "/sales"]] },
  { title: "Your account", links: [["My Reservations", "/reservations"], ["My Orders", "/orders"], ["Sign in", "/login"]] },
] as const;

export function Footer() {
  return (
    <footer className="mt-20 border-t border-line bg-brand-soft/60">
      <div className="mx-auto grid max-w-7xl gap-10 px-4 py-12 sm:px-6 md:grid-cols-[2fr_1fr_1fr]">
        <div className="max-w-sm">
          <Logo />
          <p className="mt-4 text-sm leading-relaxed text-ink-muted">
            Limited ticket drops with live countdowns. Reservations are first come, first served, and every
            ticket is held for you for 10 minutes while you check out.
          </p>
        </div>
        {COLUMNS.map((column) => (
          <div key={column.title}>
            <h2 className="text-sm font-bold">{column.title}</h2>
            <ul className="mt-3 space-y-2">
              {column.links.map(([label, to]) => (
                <li key={to}>
                  <Link to={to} className="text-sm text-ink-muted transition hover:text-brand">{label}</Link>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="border-t border-line py-5 text-center text-xs text-ink-muted">
        © {new Date().getFullYear()} FlashSaleEngine
      </div>
    </footer>
  );
}
