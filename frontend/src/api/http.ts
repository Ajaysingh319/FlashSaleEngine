import axios, { type InternalAxiosRequestConfig } from "axios";
import { tokenStore } from "../auth/tokenStore";
import { config } from "../config/env";
import type { AuthResponse } from "../types/auth";
import { toAppError } from "./toAppError";

const TIMEOUT_MS = 15_000;

/** The one HTTP client: every call goes to the API Gateway, never to a service directly. */
export const http = axios.create({ baseURL: config.apiBaseUrl, timeout: TIMEOUT_MS });

http.interceptors.request.use((request) => {
  const token = tokenStore.accessToken;
  if (token) request.headers.Authorization = `Bearer ${token}`;
  return request;
});

let refreshing: Promise<string | null> | null = null;

/**
 * Exchanges the refresh token for new tokens. Concurrent 401s share one refresh; if it fails, the session is
 * cleared (signing the user out). Uses a bare request so it never passes through the retry logic below.
 */
export function refreshAccessToken(): Promise<string | null> {
  const refreshToken = tokenStore.refreshToken;
  if (!refreshToken) return Promise.resolve(null);
  refreshing ??= axios
    .post<AuthResponse>(`${config.apiBaseUrl}/api/v1/auth/refresh`, { refreshToken }, { timeout: TIMEOUT_MS })
    .then((response) => {
      tokenStore.save(response.data);
      return response.data.accessToken;
    })
    .catch(() => {
      tokenStore.clear();
      return null;
    })
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

type RetriableRequest = InternalAxiosRequestConfig & { retriedAfterRefresh?: boolean };

http.interceptors.response.use(undefined, async (error) => {
  const request = error.config as RetriableRequest | undefined;
  const expiredSession = error.response?.status === 401 && request?.headers?.Authorization && !request.retriedAfterRefresh;
  if (request && expiredSession) {
    request.retriedAfterRefresh = true;
    const token = await refreshAccessToken();
    if (token) {
      request.headers.Authorization = `Bearer ${token}`;
      return http(request);
    }
  }
  throw toAppError(error);
});
