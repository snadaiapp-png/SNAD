/**
 * proxy.ts — Next.js 16 Proxy (replaces deprecated middleware.ts)
 * ----------------------------------------------------------------
 * Migration date: 2026-08-03
 * Previous: middleware.ts (Next.js middleware pattern, deprecated in v16)
 * Current: proxy.ts (Next.js 16 proxy pattern)
 *
 * Runtime behavior (identical to previous middleware):
 *   1. GET /crm → 307 redirect to /crm/overview
 *   2. Set snad_crm_root_entry cookie (60s TTL, sameSite lax)
 *   3. Cookie is cleared by AuthRouteRecovery in providers.tsx
 */

import { NextResponse, type NextRequest } from "next/server";

export const CRM_ROOT_ENTRY_COOKIE = "snad_crm_root_entry";

export const config = {
  matcher: ["/crm"],
};

export function proxy(request: NextRequest) {
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
