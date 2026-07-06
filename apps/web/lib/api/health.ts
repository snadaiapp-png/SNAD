/** Backend health check using the typed API client. */
import { apiClient, ApiClient } from "./client";
import { isApiClientError, isApiHttpError, ApiConfigurationError, ApiNetworkError } from "./errors";
import type { HealthCheckResult } from "./types";

interface ActuatorHealthResponse { status: string; }

const SERVER_HEALTH_TIMEOUT_MS = 60_000;

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
  const raw = process.env.BACKEND_API_BASE_URL?.trim() || "";
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

async function checkDirectBackend(baseUrl: string): Promise<{ statusCode: number; body: ActuatorHealthResponse }> {
  const response = await fetch(`${baseUrl}/actuator/health`, {
    method: "GET",
    headers: { Accept: "application/json" },
    cache: "no-store",
    signal: AbortSignal.timeout(SERVER_HEALTH_TIMEOUT_MS),
  });

  let body: ActuatorHealthResponse = { status: "UNKNOWN" };
  try {
    body = await response.json() as ActuatorHealthResponse;
  } catch {
    // A non-JSON or malformed response is not a valid Actuator health contract.
  }

  return { statusCode: response.status, body };
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

    if (serverBaseUrl && client.baseUrlValue.startsWith("/")) {
      const direct = await checkDirectBackend(serverBaseUrl);
      statusCode = direct.statusCode;
      health = direct.body;
    } else {
      health = await client.get<ActuatorHealthResponse>("/actuator/health", {
        timeoutMs: SERVER_HEALTH_TIMEOUT_MS,
        cache: "no-store",
      });
    }

    const healthy = statusCode === 200 && isHealthyResponse(health);
    return {
      configured: true,
      reachable: healthy,
      statusCode,
      targetHost,
      checkedAt,
      error: healthy ? null : `Backend health contract returned ${health.status || "UNKNOWN"}`,
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
