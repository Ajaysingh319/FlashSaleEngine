import { Loader2, LogIn } from "lucide-react";
import { useState, type FormEvent, type ReactNode } from "react";
import type { Credentials } from "../../api/authApi";

/** Email and password form shared by sign-in and registration; the page words any error. */
export function AuthForm({ title, subtitle, submitLabel, passwordHint, onSubmit, footer }: {
  title: string;
  subtitle: string;
  submitLabel: string;
  /** Shown under the password field when creating an account (enables the 8-character minimum). */
  passwordHint?: string;
  /** Resolves to an error message, or null on success. */
  onSubmit: (credentials: Credentials) => Promise<string | null>;
  footer: ReactNode;
}) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    const failure = await onSubmit({ email: email.trim(), password });
    setError(failure);
    setSubmitting(false);
  };

  const input = "w-full rounded-2xl border border-line bg-white px-4 py-3 outline-none transition focus:border-brand focus:ring-4 focus:ring-brand/10";

  return (
    <section className="px-4 py-12 sm:py-20">
      <div className="mx-auto max-w-md rounded-[2rem] border border-line bg-white p-6 shadow-card sm:p-10">
        <span className="grid size-12 place-items-center rounded-2xl bg-brand-soft text-brand">
          <LogIn className="size-6" aria-hidden />
        </span>
        <h1 className="mt-5 text-2xl font-extrabold">{title}</h1>
        <p className="mt-1 text-sm text-ink-muted">{subtitle}</p>

        <form onSubmit={submit} className="mt-8 space-y-4">
          <label className="block">
            <span className="mb-1.5 block text-sm font-semibold">Email</span>
            <input type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} className={input} />
          </label>
          <label className="block">
            <span className="mb-1.5 block text-sm font-semibold">Password</span>
            <input
              type="password"
              required
              minLength={passwordHint ? 8 : undefined}
              autoComplete={passwordHint ? "new-password" : "current-password"}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className={input}
            />
            {passwordHint && <span className="mt-1.5 block text-xs text-ink-muted">{passwordHint}</span>}
          </label>

          {error && <p role="alert" className="rounded-2xl bg-urgent-soft px-4 py-3 text-sm font-medium text-urgent">{error}</p>}

          <button
            type="submit"
            disabled={submitting}
            className="flex w-full items-center justify-center gap-2 rounded-full bg-brand px-6 py-3.5 font-bold text-white shadow-lg shadow-brand/25 transition hover:bg-brand-deep disabled:opacity-70"
          >
            {submitting && <Loader2 className="size-5 animate-spin" aria-hidden />}
            {submitLabel}
          </button>
        </form>
        <div className="mt-6 text-center text-sm text-ink-muted">{footer}</div>
      </div>
    </section>
  );
}
