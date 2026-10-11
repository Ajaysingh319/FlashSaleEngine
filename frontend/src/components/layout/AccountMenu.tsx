import { LogOut, User } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthProvider";

const iconButton = "grid size-10 place-items-center rounded-full text-ink transition hover:bg-brand-soft hover:text-brand";

/** Account icon: a sign-in link when signed out, otherwise a small menu with the account and sign-out. */
export function AccountMenu() {
  const { state, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const menu = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (event: MouseEvent) => !menu.current?.contains(event.target as Node) && setOpen(false);
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, [open]);

  if (state.status !== "signed-in") {
    return (
      <Link to="/login" className={iconButton} aria-label="Sign in" title="Sign in">
        <User className="size-5" aria-hidden />
      </Link>
    );
  }

  const { user } = state;
  const signOut = async () => {
    setOpen(false);
    await logout().catch(() => undefined);
    navigate("/");
  };

  return (
    <div className="relative" ref={menu}>
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        className="grid size-10 place-items-center rounded-full bg-brand text-sm font-bold text-white uppercase"
        aria-label="Account menu"
        aria-expanded={open}
        aria-haspopup="menu"
      >
        {(user.fullName ?? user.email).charAt(0)}
      </button>
      {open && (
        <div role="menu" className="absolute right-0 mt-2 w-64 rounded-2xl border border-line bg-white p-2 shadow-lift">
          <div className="px-3 py-2">
            <p className="truncate text-sm font-bold">{user.fullName ?? "Signed in"}</p>
            <p className="truncate text-xs text-ink-muted">{user.email}</p>
          </div>
          <button
            type="button"
            role="menuitem"
            onClick={signOut}
            className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-sm font-semibold text-urgent transition hover:bg-urgent-soft"
          >
            <LogOut className="size-4" aria-hidden /> Sign out
          </button>
        </div>
      )}
    </div>
  );
}
