import { Menu, Search, Ticket, User, X, Zap } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { Link, NavLink, useLocation, useNavigate, useSearchParams } from "react-router-dom";

const NAV = [
  { to: "/", label: "Home", end: true },
  { to: "/sales", label: "Flash Sales", end: false },
  { to: "/orders", label: "My Orders", end: false },
];

const navClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-full px-4 py-2 text-sm font-semibold transition ${isActive ? "bg-brand-soft text-brand" : "text-ink-muted hover:text-ink"}`;

export function Logo() {
  return (
    <Link to="/" className="flex items-center gap-2" aria-label="FlashSaleEngine home">
      <span className="grid size-9 place-items-center rounded-xl bg-linear-to-br from-brand to-brand-deep text-white shadow-lg shadow-brand/30">
        <Zap className="size-5" fill="currentColor" aria-hidden />
      </span>
      <span className="text-lg font-extrabold tracking-tight">
        FlashSale<span className="text-brand">Engine</span>
      </span>
    </Link>
  );
}

function SearchBox({ onSearch, className = "" }: { onSearch: () => void; className?: string }) {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [query, setQuery] = useState(params.get("q") ?? "");

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const q = query.trim();
    navigate(q ? `/sales?q=${encodeURIComponent(q)}` : "/sales");
    onSearch();
  };

  return (
    <form role="search" onSubmit={submit} className={`relative ${className}`}>
      <Search className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-ink-muted" aria-hidden />
      <input
        type="search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="Search events or cities"
        aria-label="Search events or cities"
        className="w-full rounded-full border border-line bg-brand-soft/50 py-2.5 pr-4 pl-10 text-sm transition outline-none placeholder:text-ink-muted focus:border-brand focus:bg-white"
      />
    </form>
  );
}

export function Header() {
  const [open, setOpen] = useState(false);
  const location = useLocation();
  useEffect(() => setOpen(false), [location.pathname, location.search]);

  return (
    <header className="sticky top-0 z-40 border-b border-line/70 bg-white/85 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-4 px-4 sm:px-6">
        <Logo />
        <nav className="ml-4 hidden items-center gap-1 lg:flex" aria-label="Main">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className={navClass}>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <SearchBox onSearch={() => undefined} className="ml-auto hidden w-72 md:block" />
        <div className="ml-auto flex items-center gap-1 md:ml-2">
          <Link to="/reservations" className="grid size-10 place-items-center rounded-full text-ink transition hover:bg-brand-soft hover:text-brand" aria-label="My reservations" title="My reservations">
            <Ticket className="size-5" aria-hidden />
          </Link>
          <Link to="/login" className="grid size-10 place-items-center rounded-full text-ink transition hover:bg-brand-soft hover:text-brand" aria-label="Account" title="Account">
            <User className="size-5" aria-hidden />
          </Link>
          <button
            type="button"
            onClick={() => setOpen((value) => !value)}
            className="grid size-10 place-items-center rounded-full transition hover:bg-brand-soft lg:hidden"
            aria-label={open ? "Close menu" : "Open menu"}
            aria-expanded={open}
            aria-controls="mobile-menu"
          >
            {open ? <X className="size-5" aria-hidden /> : <Menu className="size-5" aria-hidden />}
          </button>
        </div>
      </div>

      {open && (
        <div id="mobile-menu" className="border-t border-line bg-white px-4 pt-3 pb-5 lg:hidden">
          <SearchBox onSearch={() => setOpen(false)} className="mb-3 md:hidden" />
          <nav className="flex flex-col gap-1" aria-label="Mobile">
            {NAV.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className={navClass}>
                {item.label}
              </NavLink>
            ))}
            <NavLink to="/reservations" className={navClass}>My Reservations</NavLink>
          </nav>
        </div>
      )}
    </header>
  );
}
