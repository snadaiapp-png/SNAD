import { NextResponse } from "next/server";
import { checkBackendIntegration } from "@/lib/api";

/**
 * Force dynamic rendering and disable all caching for this route.
 * Health checks must always reflect the current backend state, never
 * a stale cached response.
 */
export const dynamic = "force-dynamic";
export const revalidate = 0;

/**
 * Public backend-status endpoint.
 *
 * The public response is intentionally minimal and carries NO information
 * about the backend's identity, hostname, tunnel, or URL. Previously this
 * route returned `targetHost`, which leaked the public ngrok / Render host
 * to any anonymous visitor and turned the health endpoint into a discovery
 * tool for the platform's internal topology. Operational details (host,
 * error, raw upstream body) remain in server logs and in the underlying
 * `HealthCheckResult`; they are not surfaced to anonymous callers.
 *
 * Authenticated operators needing richer detail should consume a separate
 * admin-only route or observability backend rather than this public probe.
 */
export async function GET() {
  const result = await checkBackendIntegration();
  return NextResponse.json(
    {
      configured: result.configured,
      reachable: result.reachable,
      statusCode: result.statusCode,
      checkedAt: result.checkedAt,
    },
    {
      headers: {
        "Cache-Control": "no-store, no-cache, must-revalidate, max-age=0",
        Pragma: "no-cache",
      },
    }
  );
}
