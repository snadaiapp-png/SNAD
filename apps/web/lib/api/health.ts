/** Backend health check using the typed API client. */
import { apiClient, ApiClient } from "./client";
import { isApiClientError, isApiHttpError, ApiConfigurationError, ApiNetworkError } from "./errors";
import type { HealthCheckResult } from "./types";

interface ActuatorHealthResponse { status: string; }

/**
 * Extract a safe hostname (with non-standard port if present) from a base URL.
 * Returns `null` if the URL cannot be parsed.
 *
 * Only the host (and port if non-standard) are returned — never the scheme,
 * path, query, or credentials.
 */
function extractTargetHost(baseUrl: string): string | null {
  if (!baseUrl) return null;
  try {
    const parsed = new URL(baseUrl);
    // Include port only if it's non-standard for the protocol.
    const isStandardPort =
      (parsed.protocol === "https:" && parsed.port === "443") ||
      (parsed.protocol === "http:" && parsed.port === "80");
    return isStandardPort || !parsed.port ? parsed.hostname : `${parsed.hostname}:${parsed.port}`;
  } catch {
    return null;
  }
}

/** Current UTC timestamp in ISO-8601 format. */
function nowUtcIso(): string {
  return new Date().toISOString();
}

export async function checkBackendIntegration(client: ApiClient = apiClient): Promise<HealthCheckResult> {
  const checkedAt = nowUtcIso();

  if (!client.isConfigured) {
    return {
      configured: false,
      reachable: false,
      statusCode: null,
      targetHost: null,
      checkedAt,
      error: "NEXT_PUBLIC_API_BASE_URL is not set",
    };
  }

  const targetHost = extractTargetHost(client.baseUrlValue);

  try {
    // Use cache: "no-store" to prevent any caching of the health response
    // by fetch layers, CDNs, or Next.js fetch cache.
    await client.get<ActuatorHealthResponse>("/actuator/health", {
      timeoutMs: 10_000,
      cache: "no-store",
    });
    return {
      configured: true,
      reachable: true,
      statusCode: 200,
      targetHost,
      checkedAt,
      error: null,
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
    return { configured: true, reachable: false, statusCode: null, targetHost, checkedAt, error: err instanceof Error ? err.message : String(err) };
  }
}
