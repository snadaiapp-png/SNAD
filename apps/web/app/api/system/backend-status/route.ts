import { NextResponse } from "next/server";
import { ApiClient, checkBackendIntegration } from "@/lib/api";

/**
 * Force dynamic rendering and disable all caching for this route.
 * Health checks must always reflect the current backend state, never
 * a stale cached response.
 */
export const dynamic = "force-dynamic";
export const revalidate = 0;

function backendHealthClient(): ApiClient {
  const baseUrl =
    process.env.BACKEND_API_BASE_URL ||
    process.env.NEXT_PUBLIC_API_BASE_URL ||
    "";

  return new ApiClient({ baseUrl, timeoutMs: 10_000 });
}

export async function GET() {
  // Do not use the browser-facing production API client here. In production
  // that client intentionally points to the relative same-origin BFF path
  // (/api/platform), which cannot be fetched as a relative URL from the
  // server runtime. The diagnostic route must probe the configured upstream
  // backend directly using the server-only BACKEND_API_BASE_URL.
  const result = await checkBackendIntegration(backendHealthClient());

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
    },
  );
}
