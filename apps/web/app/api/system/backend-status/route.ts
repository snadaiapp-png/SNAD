import { NextResponse } from "next/server";
import { checkBackendIntegration } from "@/lib/api-integration";

/**
 * Frontend integration verification route.
 *
 * Calls the backend health endpoint via the configured API base URL
 * and returns a non-sensitive status object. This route is used by:
 *   - The production smoke workflow to verify frontend→backend connectivity
 *   - Developers to check if the API is configured and reachable
 *
 * Returns only:
 *   { "configured": boolean, "reachable": boolean, "statusCode": number | null }
 *
 * Never returns: secrets, internal URLs, database data, stack traces.
 */
export async function GET() {
  const result = await checkBackendIntegration();

  return NextResponse.json({
    configured: result.configured,
    reachable: result.reachable,
    statusCode: result.statusCode,
  });
}
