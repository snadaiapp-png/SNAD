/** Environment-aware API configuration for SANAD. */
import { ApiConfigurationError } from "./errors";

export const DEFAULT_API_TIMEOUT_MS = 60_000;
const ALLOWED_PROTOCOLS = new Set(["https:", "http:"]);
const LOCALHOST_HOSTS = new Set(["localhost", "127.0.0.1", "0.0.0.0", "::1"]);
const PRODUCTION_BFF_BASE_URL = "/api/platform";

function readBaseUrl(): string {
  // Production browser traffic must use the same-origin Next.js BFF. This keeps
  // refresh tokens in an HttpOnly first-party cookie and avoids cross-site
  // cookie/CORS failures between Vercel and the Render API.
  if (process.env.NODE_ENV === "production") return PRODUCTION_BFF_BASE_URL;

  const raw = process.env.NEXT_PUBLIC_API_BASE_URL || "";
  return raw ? raw.replace(/\/+$/, "") : "";
}

export const API_BASE_URL = readBaseUrl();
export const IS_API_CONFIGURED = API_BASE_URL.length > 0;

export function validateBaseUrl(baseUrl: string): void {
  if (!baseUrl) throw new ApiConfigurationError("NEXT_PUBLIC_API_BASE_URL is not set.");

  if (baseUrl.startsWith("/")) {
    if (baseUrl.startsWith("//") || baseUrl.includes("?") || baseUrl.includes("#")) {
      throw new ApiConfigurationError("Relative API base URL must be a safe same-origin path.");
    }
    if (!baseUrl.startsWith("/api/")) {
      throw new ApiConfigurationError("Relative API base URL must be under /api/.");
    }
    return;
  }

  let parsed: URL;
  try { parsed = new URL(baseUrl); }
  catch { throw new ApiConfigurationError("NEXT_PUBLIC_API_BASE_URL is not a valid URL."); }
  if (!ALLOWED_PROTOCOLS.has(parsed.protocol)) {
    throw new ApiConfigurationError("NEXT_PUBLIC_API_BASE_URL must use http: or https:.");
  }
  if (parsed.protocol === "http:" && !LOCALHOST_HOSTS.has(parsed.hostname)) {
    throw new ApiConfigurationError("Use https: for non-local API hosts.");
  }
  if (parsed.username || parsed.password) {
    throw new ApiConfigurationError("NEXT_PUBLIC_API_BASE_URL must not contain credentials.");
  }
}

export function buildSearchParams(params: Record<string, unknown>): URLSearchParams {
  const result = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined) continue;
    if (Array.isArray(value)) {
      for (const item of value) if (item !== undefined) result.append(key, String(item));
    } else result.append(key, String(value));
  }
  return result;
}

export function buildUrl(baseUrl: string, path: string, query?: Record<string, unknown>): string {
  validateBaseUrl(baseUrl);
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  const joined = `${baseUrl.replace(/\/+$/, "")}${normalizedPath}`;
  if (!query) return joined;
  const params = buildSearchParams(query).toString();
  return params ? `${joined}?${params}` : joined;
}

export function buildApiUrl(path: string): string {
  if (!IS_API_CONFIGURED) return "";
  return `${API_BASE_URL}${path.startsWith("/") ? path : `/${path}`}`;
}

export const API_FETCH_OPTIONS: RequestInit = {
  credentials: "include",
  headers: { "Content-Type": "application/json", Accept: "application/json" },
};
export const API_TIMEOUT_MS = DEFAULT_API_TIMEOUT_MS;
