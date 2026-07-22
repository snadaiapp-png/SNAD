/** Backend health check using the typed API client. */
import { apiClient, ApiClient } from "./client";
import { isApiClientError, isApiHttpError, ApiConfigurationError, ApiNetworkError } from "./errors";
import type { HealthCheckResult } from "./types";

interface ActuatorHealthResponse { status: string; }

// Hosted backends are probed within the bounded BFF timeout contract.
// Keep the probe bounded by the same server-side timeout contract as the BFF.
const DEFAULT_HEALTH_TIMEOUT_MS = 15_000;
const MIN_HEALTH_TIMEOUT_MS = 1_000;
const MAX_HEALTH_TIMEOUT_MS = 25_000;
const SERVER_HEALTH_MAX_ATTEMPTS = 2;
const SERVER_HEALTH_RETRY_DELAY_MS = 250;
const PRODUCTION_BACKEND_URL = "https://sanad-backend-mcrj.onrender.com";

function healthTimeoutMs(): number {
  const raw = process.env.BACKEND_REQUEST_TIMEOUT_MS || "";
  const parsed = Number.parseInt(raw, 10);
  if (!Number.isFinite(parsed)) return DEFAULT_HEALTH_TIMEOUT_MS;
  return Math.min(MAX_HEALTH_TIMEOUT_MS, Math.max(MIN_HEALTH_TIMEOUT_MS, parsed));
}

/**
 * Extract a safe hostname (with non-standard port if present) from a base URL.
 * Returns `null` if the URL cannot be parsed.
 *
 * Only the host (and port if non-standard) are returned — never the scheme,
 * path, query, or credentials.
 */
function extractTargetHost(baseUrl: string): string | null {
  if (!baseUrl || baseUrl.startsWith("/")) return null;
  try {
    const parsed = new URL(baseUrl);
    const isStandardPort =
      (parsed.protocol === "https:" && parsed.port === "443") ||
      (parsed.protocol === "http:" && parsed.port === "80");
    return isStandardPort || !parsed.port ? parsed.hostname : `${parsed.hostname}:${parsed.port}`;
  } catch {
    return null;
  }
}

function readServerBackendBaseUrl(): string {
  // Production routing is immutable and must not depend on stale Vercel variables.
  if (process.env.VERCEL_ENV === "production") return PRODUCTION_BACKEND_URL;

  // Try BACKEND_API_BASE_URL first (server-side env var), then fall back to
  // NEXT_PUBLIC_API_BASE_URL (which may also be set on Vercel to the backend URL).
  const raw = (process.env.BACKEND_API_BASE_URL || process.env.NEXT_PUBLIC_API_BASE_URL || "").trim();
  if (!raw || raw.startsWith("/")) return "";

  try {
    const parsed = new URL(raw);
    const isLocal = ["localhost", "127.0.0.1", "::1"].includes(parsed.hostname);
    if (parsed.protocol !== "https:" && !(isLocal && parsed.protocol === "http:")) return "";
    if (parsed.username || parsed.password) return "";
    parsed.pathname = parsed.pathname.replace(/\/+$/, "");
    parsed.search = "";
    parsed.hash = "";
    return parsed.toString().replace(/\/$/, "");
  } catch {
    return "";
  }
}

/** Current UTC timestamp in ISO-8601 format. */
function nowUtcIso(): string {
  return new Date().toISOString();
}

function isHealthyResponse(value: ActuatorHealthResponse): boolean {
  return value?.status === "UP";
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function checkDirectBackendOnce(baseUrl: string): Promise<{ statusCode: number; body: ActuatorHealthResponse }> {
  const response = await fetch(`${baseUrl}/actuator/health`, {
    method: "GET",
    headers: { Accept: "application/json" },
    cache: "no-store",
    signal: AbortSignal.timeout(healthTimeoutMs()),
  });

  let body: ActuatorHealthResponse = { status: "UNKNOWN" };
  try {
    body = await response.json() as ActuatorHealthResponse;
  } catch {
    // A non-JSON or malformed response is not a valid Actuator health contract.
  }

  return { statusCode: response.status, body };
}

async function checkDirectBackend(baseUrl: string): Promise<{ statusCode: number; body: ActuatorHealthResponse }> {
  let lastError: unknown;

  for (let attempt = 1; attempt <= SERVER_HEALTH_MAX_ATTEMPTS; attempt += 1) {
    try {
      const result = await checkDirectBackendOnce(baseUrl);
      if (result.statusCode < 500 || attempt === SERVER_HEALTH_MAX_ATTEMPTS) return result;
    } catch (err) {
      lastError = err;
      if (attempt === SERVER_HEALTH_MAX_ATTEMPTS) throw err;
    }

    await sleep(SERVER_HEALTH_RETRY_DELAY_MS);
  }

  throw lastError instanceof Error ? lastError : new Error("Backend health check failed");
}

/**
 * Probe the backend health via the BFF by hitting an unauthenticated endpoint
 * that the BFF allows (/api/v1/auth/me). A 401 response proves the BFF→Backend
 * chain is operational. This is used as a fallback when no direct backend URL
 * is available (e.g., browser-side or when BACKEND_API_BASE_URL is not set).
 */
async function checkBackendViaBff(client: ApiClient): Promise<{ statusCode: number; reachable: boolean }> {
  try {
    // /api/v1/auth/me without auth returns 401 if backend is reachable.
    // We deliberately catch the 401 as a "reachable" signal.
    await client.get("/api/v1/auth/me", {
      timeoutMs: healthTimeoutMs(),
      cache: "no-store",
    });
    // If we get here, the backend returned 200 (unlikely without token, but
    // treat it as reachable).
    return { statusCode: 200, reachable: true };
  } catch (err) {
    if (isApiHttpError(err)) {
      // 401 means the backend is alive and rejecting unauthenticated requests.
      // 403 would also indicate the backend is alive.
      if (err.status === 401 || err.status === 403) {
        return { statusCode: err.status, reachable: true };
      }
      // Any other HTTP status means the backend responded (alive) but with
      // an unexpected code. Treat 5xx as unreachable, others as reachable.
      if (err.status >= 500) {
        return { statusCode: err.status, reachable: false };
      }
      return { statusCode: err.status, reachable: true };
    }
    // Network error means backend is unreachable.
    throw err;
  }
}

export async function checkBackendIntegration(client: ApiClient = apiClient): Promise<HealthCheckResult> {
  const checkedAt = nowUtcIso();
  const serverBaseUrl = readServerBackendBaseUrl();
  const effectiveBaseUrl = client.baseUrlValue.startsWith("/") && serverBaseUrl
    ? serverBaseUrl
    : client.baseUrlValue;
  const configured = client.isConfigured || Boolean(serverBaseUrl);

  if (!configured) {
    return {
      configured: false,
      reachable: false,
      statusCode: null,
      targetHost: null,
      checkedAt,
      error: "Backend API base URL is not set",
    };
  }

  const targetHost = extractTargetHost(effectiveBaseUrl);

  try {
    let statusCode = 200;
    let health: ActuatorHealthResponse;
    let reachable: boolean;

    if (serverBaseUrl && client.baseUrlValue.startsWith("/")) {
      // Direct backend access — use Actuator health endpoint.
      const direct = await checkDirectBackend(serverBaseUrl);
      statusCode = direct.statusCode;
      health = direct.body;
      reachable = statusCode === 200 && isHealthyResponse(health);
    } else if (client.baseUrlValue.startsWith("/")) {
      // BFF mode — no direct backend URL available. Probe via BFF using
      // an unauthenticated endpoint that the BFF allows.
      const probe = await checkBackendViaBff(client);
      statusCode = probe.statusCode;
      reachable = probe.reachable;
      health = { status: reachable ? "UP" : "DOWN" };
    } else {
      // Direct client mode — use Actuator health endpoint.
      health = await client.get<ActuatorHealthResponse>("/actuator/health", {
        timeoutMs: healthTimeoutMs(),
        cache: "no-store",
      });
      statusCode = 200; // client.get throws on non-2xx, so 200 is implied
      reachable = isHealthyResponse(health);
    }

    return {
      configured: true,
      reachable,
      statusCode,
      targetHost,
      checkedAt,
      error: reachable ? null : `Backend health check failed (status: ${health.status || "UNKNOWN"})`,
    };
  } catch (err) {
    if (err instanceof ApiConfigurationError) {
      return { configured: true, reachable: false, statusCode: null, targetHost, checkedAt, error: err.message };
    }
    if (isApiHttpError(err)) {
      return { configured: true, reachable: false, statusCode: err.status, targetHost, checkedAt, error: err.toSafeSummary() };
    }
    if (err instanceof ApiNetworkError && err.cause instanceof Error) {
      return { configured: true, reachable: false, statusCode: null, targetHost, checkedAt, error: err.cause.message };
    }
    if (isApiClientError(err)) {
      return { configured: true, reachable: false, statusCode: null, targetHost, checkedAt, error: err.toSafeSummary() };
    }
    return {
      configured: true,
      reachable: false,
      statusCode: null,
      targetHost,
      checkedAt,
      error: err instanceof Error ? err.message : String(err),
    };
  }
}
