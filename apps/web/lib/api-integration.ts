/**
 * Frontend integration verification component.
 *
 * Uses the API configuration from api-config.ts to verify
 * that the frontend can reach the backend. This is a
 * client-side utility for development and smoke testing.
 */

import { API_BASE_URL, IS_API_CONFIGURED, buildApiUrl } from "./api-config";

export interface IntegrationCheckResult {
  configured: boolean;
  reachable: boolean;
  statusCode: number | null;
  error: string | null;
}

/**
 * Verify that the backend API is reachable from the frontend.
 *
 * Calls the health endpoint (/actuator/health) which is the
 * only unauthenticated, non-sensitive public endpoint.
 *
 * @returns Integration check result
 */
export async function checkBackendIntegration(): Promise<IntegrationCheckResult> {
  if (!IS_API_CONFIGURED) {
    return {
      configured: false,
      reachable: false,
      statusCode: null,
      error: "NEXT_PUBLIC_API_BASE_URL is not set",
    };
  }

  try {
    const healthUrl = buildApiUrl("/actuator/health");
    const response = await fetch(healthUrl, {
      method: "GET",
      credentials: "include",
      signal: AbortSignal.timeout(10000),
    });

    return {
      configured: true,
      reachable: response.ok,
      statusCode: response.status,
      error: response.ok ? null : `HTTP ${response.status}`,
    };
  } catch (err) {
    return {
      configured: true,
      reachable: false,
      statusCode: null,
      error: err instanceof Error ? err.message : String(err),
    };
  }
}

export { API_BASE_URL, IS_API_CONFIGURED };
