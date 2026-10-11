/** Only in-app paths are allowed as post-login destinations, so ?redirect= cannot send users to another site. */
export function safeRedirect(target: string | null, fallback = "/"): string {
  return target && target.startsWith("/") && !target.startsWith("//") && !target.startsWith("/\\") ? target : fallback;
}

export const loginPath = (returnTo: string) => `/login?redirect=${encodeURIComponent(returnTo)}`;
