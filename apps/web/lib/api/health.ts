/** Backend health check using the typed API client. */
import { apiClient, ApiClient } from "./client";
import { isApiClientError, isApiHttpError, ApiConfigurationError } from "./errors";
import type { HealthCheckResult } from "./types";

interface ActuatorHealthResponse { status: string; }

export async function checkBackendIntegration(client: ApiClient = apiClient): Promise<HealthCheckResult> {
  if (!client.isConfigured) {
    return { configured: false, reachable: false, statusCode: null, error: "NEXT_PUBLIC_API_BASE_URL is not set" };
  }
  try {
    await client.get<ActuatorHealthResponse>("/actuator/health", { timeoutMs: 10_000 });
    return { configured: true, reachable: true, statusCode: 200, error: null };
  } catch (err) {
    if (err instanceof ApiConfigurationError) {
      return { configured: true, reachable: false, statusCode: null, error: err.message };
    }
    if (isApiHttpError(err)) {
      return { configured: true, reachable: false, statusCode: err.status, error: err.toSafeSummary() };
    }
    if (isApiClientError(err)) {
      return { configured: true, reachable: false, statusCode: null, error: err.toSafeSummary() };
    }
    return { configured: true, reachable: false, statusCode: null, error: err instanceof Error ? err.message : String(err) };
  }
}
