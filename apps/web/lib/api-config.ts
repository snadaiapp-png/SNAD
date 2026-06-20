/**
 * SANAD API client configuration.
 *
 * Reads the backend API base URL from the NEXT_PUBLIC_API_BASE_URL
 * environment variable. This variable is set in:
 *   - Local development: .env.local
 *   - Vercel preview: Vercel project settings
 *   - Vercel production: Vercel project settings
 *
 * The URL must not have a trailing slash; it is normalized on use.
 */

const rawUrl = process.env.NEXT_PUBLIC_API_BASE_URL || "";

/**
 * Normalized API base URL without trailing slash.
 * Empty string if not configured (frontend can run standalone).
 */
export const API_BASE_URL = rawUrl.replace(/\/+$/, "");

/**
 * Whether the API is configured.
 * Used to conditionally show/hide backend-dependent features.
 */
export const IS_API_CONFIGURED = API_BASE_URL.length > 0;

/**
 * Build a full API URL from a path.
 *
 * @param path - API path starting with / (e.g. "/api/v1/organizations")
 * @returns Full URL, or empty string if API is not configured
 */
export function buildApiUrl(path: string): string {
  if (!IS_API_CONFIGURED) return "";
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${API_BASE_URL}${normalizedPath}`;
}

/**
 * Default fetch options for API calls.
 * Includes credentials, timeout, and JSON headers.
 */
export const API_FETCH_OPTIONS: RequestInit = {
  credentials: "include",
  headers: {
    "Content-Type": "application/json",
    Accept: "application/json",
  },
};

/**
 * API request timeout in milliseconds.
 */
export const API_TIMEOUT_MS = 10000;
