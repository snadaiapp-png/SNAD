import { NextResponse } from "next/server";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const RELEASE_CONTRACT_VERSION = "2";

export function GET(): NextResponse {
  const commitSha = process.env.VERCEL_GIT_COMMIT_SHA || process.env.GITHUB_SHA || "UNKNOWN";
  const commitRef = process.env.VERCEL_GIT_COMMIT_REF || "UNKNOWN";
  const environment = process.env.VERCEL_TARGET_ENV || process.env.VERCEL_ENV || process.env.NODE_ENV || "UNKNOWN";

  return NextResponse.json(
    {
      service: "SNAD Web",
      contractVersion: RELEASE_CONTRACT_VERSION,
      commitSha,
      commitRef,
      environment,
    },
    {
      status: 200,
      headers: {
        "Cache-Control": "no-store, no-cache, must-revalidate, max-age=0",
        Pragma: "no-cache",
      },
    },
  );
}
