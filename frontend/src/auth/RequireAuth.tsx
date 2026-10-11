import { Loader2 } from "lucide-react";
import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { config } from "../config/env";
import { loginPath } from "../utils/redirect";
import { useAuth } from "./AuthProvider";

/** Account pages need a signed-in user in api mode; signed-out visitors go to sign-in and come back afterwards. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  const location = useLocation();
  if (config.dataMode === "demo" || state.status === "signed-in") return <>{children}</>;
  if (state.status === "restoring") {
    return (
      <div className="flex justify-center py-24 text-ink-muted" role="status">
        <Loader2 className="size-6 animate-spin" aria-label="Checking your sign-in" />
      </div>
    );
  }
  return <Navigate to={loginPath(location.pathname + location.search)} replace />;
}
