import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { authApi, type Credentials } from "../api/authApi";
import { refreshAccessToken } from "../api/http";
import { config } from "../config/env";
import type { AuthResponse, UserProfile } from "../types/auth";
import { tokenStore } from "./tokenStore";

export type AuthState =
  | { status: "restoring" }
  | { status: "signed-out" }
  | { status: "signed-in"; user: UserProfile };

interface AuthContextValue {
  state: AuthState;
  login: (credentials: Credentials) => Promise<void>;
  register: (credentials: Credentials) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Signs users in against Auth Service (api mode only; demo mode has no accounts). After a reload the session is
 * restored from the refresh token; if the backend rejects a refresh, the user is signed out everywhere at once.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(
    config.dataMode === "api" && tokenStore.refreshToken ? { status: "restoring" } : { status: "signed-out" },
  );

  const loadProfile = useCallback(async () => {
    setState({ status: "signed-in", user: await authApi.me() });
  }, []);

  useEffect(() => {
    if (state.status !== "restoring") return;
    refreshAccessToken()
      .then((token) => (token ? loadProfile() : setState({ status: "signed-out" })))
      .catch(() => {
        tokenStore.clear();
        setState({ status: "signed-out" });
      });
    // Runs once, on start-up.
  }, []);

  // A failed refresh anywhere (e.g. during a reservation) clears the tokens: reflect that here.
  useEffect(
    () => tokenStore.subscribe(() => {
      if (!tokenStore.accessToken && !tokenStore.refreshToken) setState({ status: "signed-out" });
    }),
    [],
  );

  const signIn = useCallback(
    async (request: Promise<AuthResponse>) => {
      tokenStore.save(await request);
      try {
        await loadProfile();
      } catch (error) {
        tokenStore.clear();
        throw error;
      }
    },
    [loadProfile],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      state,
      login: (credentials) => signIn(authApi.login(credentials)),
      register: (credentials) => signIn(authApi.register(credentials)),
      logout: async () => {
        try {
          await authApi.logout();
        } finally {
          tokenStore.clear();
        }
      },
    }),
    [state, signIn],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}
