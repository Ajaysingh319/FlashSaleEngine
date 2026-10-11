import type { AuthResponse } from "../types/auth";

const REFRESH_KEY = "flashsale.refreshToken";

/**
 * Holds the session's tokens. The access token stays in memory only, so it is never written to storage. The
 * refresh token lives in sessionStorage: it survives a page reload but is gone when the tab closes.
 */
class TokenStore {
  private access: string | null = null;
  private readonly listeners = new Set<() => void>();

  get accessToken(): string | null {
    return this.access;
  }

  get refreshToken(): string | null {
    try {
      return sessionStorage.getItem(REFRESH_KEY);
    } catch {
      return null;
    }
  }

  save(tokens: AuthResponse): void {
    this.access = tokens.accessToken;
    try {
      sessionStorage.setItem(REFRESH_KEY, tokens.refreshToken);
    } catch {
      // Storage blocked: the session simply ends with this page.
    }
    this.notify();
  }

  clear(): void {
    this.access = null;
    try {
      sessionStorage.removeItem(REFRESH_KEY);
    } catch {
      // nothing stored
    }
    this.notify();
  }

  /** Called whenever tokens are saved or cleared (for example when a refresh fails). */
  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify(): void {
    this.listeners.forEach((listener) => listener());
  }
}

export const tokenStore = new TokenStore();
