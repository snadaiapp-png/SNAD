/**
 * proxy.ts — Next.js 16 Proxy (replaces deprecated middleware.ts)
 * ----------------------------------------------------------------
 * Runtime invariants:
 *   1. Production may only serve a Git-linked deployment sourced from main.
 *      If Vercel reports a non-main Git ref in production, fail closed with
 *      HTTP 503 for all application routes except the release identity probe.
 *   2. GET /crm → 307 redirect to /crm/overview and preserve root intent.
 */

import { NextResponse, type NextRequest } from "next/server";

export const CRM_ROOT_ENTRY_COOKIE = "snad_crm_root_entry";
export const RELEASE_GUARD_HEADER = "x-snad-release-guard";

type ReleaseEnv = {
  VERCEL_TARGET_ENV?: string;
  VERCEL_ENV?: string;
  VERCEL_GIT_COMMIT_REF?: string;
};

export function isUnauthorizedProductionSource(env: ReleaseEnv = process.env): boolean {
  const target = (env.VERCEL_TARGET_ENV || env.VERCEL_ENV || "").toLowerCase();
  const ref = (env.VERCEL_GIT_COMMIT_REF || "").trim();
  return (target === "production" || target === "prod") && ref !== "" && ref !== "main";
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|api/system/release).*)"],
};

export function proxy(request: NextRequest) {
  if (isUnauthorizedProductionSource()) {
    return new NextResponse("SNAD production deployment is not authorized for this source branch.", {
      status: 503,
      headers: {
        "Cache-Control": "no-store, no-cache, must-revalidate, max-age=0",
        [RELEASE_GUARD_HEADER]: "blocked-non-main-production-source",
      },
    });
  }

  if (request.nextUrl.pathname !== "/crm") {
    return NextResponse.next();
  }

  const destination = request.nextUrl.clone();
  destination.pathname = "/crm/overview";
  destination.search = "";

  const response = NextResponse.redirect(destination, 307);
  response.cookies.set({
    name: CRM_ROOT_ENTRY_COOKIE,
    value: "1",
    path: "/",
    maxAge: 60,
    sameSite: "lax",
    secure: request.nextUrl.protocol === "https:",
  });
  return response;
}
