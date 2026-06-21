import { NextResponse } from "next/server";
import { checkBackendIntegration } from "@/lib/api";

/**
 * Force dynamic rendering and disable all caching for this route.
 * Health checks must always reflect the current backend state, never
 * a stale cached response.
 */
export const dynamic = "force-dynamic";
export const revalidate = 0;

export async function GET() {
  const result = await checkBackendIntegration();
  return NextResponse.json(
    {
      configured: result.configured,
      reachable: result.reachable,
      statusCode: result.statusCode,
      targetHost: result.targetHost,
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
