import { NextResponse } from "next/server";

/**
 * Keep-alive endpoint for the SANAD backend on Render.
 *
 * Render free-tier services spin down after ~15 minutes of inactivity.
 * Cold starts take 30-60+ seconds, which exceeds the BFF's upstream
 * timeout budget and produces HTTP 504 on auth refresh.
 *
 * A Vercel cron job calls this endpoint every 10 minutes. The endpoint
 * pings the backend health check to keep the Render service awake.
 *
 * This endpoint is excluded from the platform BFF catch-all by its
 * path (/api/keepalive vs /api/platform/...).
 */

const PRODUCTION_BACKEND_URL = "https://sanad-backend-mcrj.onrender.com";
const HEALTH_PATH = "/actuator/health";
const PING_TIMEOUT_MS = 10_000;

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(): Promise<NextResponse> {
  const start = Date.now();
  try {
    const target = `${PRODUCTION_BACKEND_URL}${HEALTH_PATH}`;
    const res = await fetch(target, {
      method: "GET",
      signal: AbortSignal.timeout(PING_TIMEOUT_MS),
      cache: "no-store",
    });
    const elapsed = Date.now() - start;
    return NextResponse.json({
      status: res.ok ? "awake" : "degraded",
      backend: res.status,
      elapsed,
      timestamp: new Date().toISOString(),
    });
  } catch (error) {
    const elapsed = Date.now() - start;
    return NextResponse.json({
      status: "cold-start-or-unreachable",
      error: error instanceof Error ? error.message : "unknown",
      elapsed,
      timestamp: new Date().toISOString(),
    }, { status: 200 });
  }
}
