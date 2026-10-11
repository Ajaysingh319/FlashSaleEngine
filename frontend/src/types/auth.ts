/** Mirrors Auth Service (PRD 16). */

/** POST /api/v1/auth/login, /register and /refresh */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  /** Access-token lifetime in milliseconds. */
  expiresIn: number;
}

/** GET /api/v1/users/me */
export interface UserProfile {
  id: string;
  email: string;
  fullName: string | null;
  role: "CUSTOMER" | "ADMIN";
  createdAt: string;
}
