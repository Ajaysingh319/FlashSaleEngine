import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { AuthForm } from "../components/auth/AuthForm";
import { config } from "../config/env";
import { AppError } from "../data/errors";
import { safeRedirect } from "../utils/redirect";

/** Auth Service answers a wrong password with 403 (it has no dedicated handler yet), so 401 and 403 read the same. */
function loginError(error: unknown): string {
  const { kind, message } = AppError.from(error);
  if (kind === "unauthorized" || kind === "forbidden") return "Email or password is incorrect.";
  if (kind === "invalid") return message;
  if (kind === "rate-limited") return "Too many sign-in attempts. Please wait a minute and try again.";
  if (kind === "network") return "Could not reach the server. Check your connection and try again.";
  return "Sign-in failed. Please try again.";
}

/** An already-registered email currently comes back as 403 without details, so the wording stays careful. */
function registerError(error: unknown): string {
  const { kind, message } = AppError.from(error);
  if (kind === "invalid") return message;
  if (kind === "forbidden" || kind === "unknown") return "We could not create your account. This email may already be registered.";
  if (kind === "network") return "Could not reach the server. Check your connection and try again.";
  return "Registration failed. Please try again.";
}

function useAuthRedirect() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const redirect = safeRedirect(params.get("redirect"));
  const withRedirect = (path: string) => (redirect === "/" ? path : `${path}?redirect=${encodeURIComponent(redirect)}`);
  return { redirect, withRedirect, done: () => navigate(redirect, { replace: true }) };
}

function DemoModeNotice() {
  return (
    <section className="mx-auto max-w-md px-4 py-20 text-center">
      <h1 className="text-2xl font-extrabold">Accounts are off in demo mode</h1>
      <p className="mt-3 text-ink-muted">Demo mode needs no sign-in. Set VITE_DATA_MODE=api to sign in against the real backend.</p>
      <Link to="/" className="mt-6 inline-block rounded-full bg-brand px-6 py-3 text-sm font-bold text-white">Back to flash sales</Link>
    </section>
  );
}

export function LoginPage() {
  const { state, login } = useAuth();
  const { redirect, withRedirect, done } = useAuthRedirect();
  if (config.dataMode === "demo") return <DemoModeNotice />;
  if (state.status === "signed-in") return <Navigate to={redirect} replace />;

  return (
    <AuthForm
      title="Welcome back"
      subtitle="Sign in to reserve tickets before they are gone."
      submitLabel="Sign in"
      onSubmit={async (credentials) => {
        try {
          await login(credentials);
          done();
          return null;
        } catch (error) {
          return loginError(error);
        }
      }}
      footer={<>New here? <Link to={withRedirect("/register")} className="font-semibold text-brand">Create an account</Link></>}
    />
  );
}

export function RegisterPage() {
  const { state, register } = useAuth();
  const { redirect, withRedirect, done } = useAuthRedirect();
  if (config.dataMode === "demo") return <DemoModeNotice />;
  if (state.status === "signed-in") return <Navigate to={redirect} replace />;

  return (
    <AuthForm
      title="Create your account"
      subtitle="One account for every flash sale."
      submitLabel="Create account"
      passwordHint="At least 8 characters."
      onSubmit={async (credentials) => {
        try {
          await register(credentials);
          done();
          return null;
        } catch (error) {
          return registerError(error);
        }
      }}
      footer={<>Already have an account? <Link to={withRedirect("/login")} className="font-semibold text-brand">Sign in</Link></>}
    />
  );
}
