/** "demo": isolated sample data. "api": real data through the API Gateway. Never mixed. */
export type DataMode = "demo" | "api";

function parseDataMode(value: string | undefined): DataMode {
  const mode = (value ?? "demo").trim().toLowerCase();
  if (mode !== "demo" && mode !== "api") {
    throw new Error(`VITE_DATA_MODE must be "demo" or "api", not "${value}"`);
  }
  return mode;
}

export const config = {
  dataMode: parseDataMode(import.meta.env.VITE_DATA_MODE),
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080",
  currency: import.meta.env.VITE_CURRENCY ?? "INR",
} as const;
