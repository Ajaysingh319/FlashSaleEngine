import type { AuthResponse, UserProfile } from "../types/auth";
import { http } from "./http";

export interface Credentials {
  email: string;
  password: string;
}

/** Auth Service endpoints (PRD 16), all through the Gateway. */
export const authApi = {
  login: async (credentials: Credentials) => (await http.post<AuthResponse>("/api/v1/auth/login", credentials)).data,
  register: async (credentials: Credentials) => (await http.post<AuthResponse>("/api/v1/auth/register", credentials)).data,
  /** Stateless JWT: the server keeps no session, so this only lets it record the sign-out. */
  logout: async () => {
    await http.post("/api/v1/auth/logout");
  },
  me: async () => (await http.get<UserProfile>("/api/v1/users/me")).data,
};
