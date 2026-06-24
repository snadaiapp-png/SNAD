/**
 * SANAD — Server-side route protection middleware
 * ------------------------------------------------------------
 * DEFECT-019 remediation: prevents flash of unprotected content
 * and SEO indexing of protected pages by validating auth state
 * on the server before the page is rendered.
 *
 * Strategy:
 * 1. Access tokens are NEVER stored in cookies or localStorage
 *    (DEFECT-013 fix). They live only in React memory.
 * 2. The backend issues an HttpOnly refresh cookie (sanad_refresh)
 *    on login. Presence of this cookie on the request is a strong
 *    signal that the user has an active session.
 * 3. This middleware does NOT validate the cookie cryptographically
 *    — the backend does that via /api/v1/auth/refresh on demand.
 *    The middleware only blocks obviously-anonymous requests from
 *    reaching protected pages.
 * 4. Unauthenticated users are redirected to the home page (/),
 *    which renders the AuthBoundary (login form) client-side.
 *
 * Public paths:
 *   - /              (login / signup screen)
 *   - /reset-password (password reset flow)
 *   - /api/*         (Next.js API routes — handle their own auth)
 *   - /_next/*       (Next.js internal assets)
 *   - /favicon.ico, /vercel.svg, /file.svg, /globe.svg, /next.svg, /window.svg
 *
 * The middleware runs on the Node.js runtime (not Edge) so it can
 * inspect cookies reliably and stay compatible with future
 * server-side token validation logic.
 *
 * Author: SANAD-FDP-001 / DEFECT-019
 */

import { NextResponse, type NextRequest } from "next/server";

const REFRESH_COOKIE_NAME = "sanad_refresh";

const PUBLIC_PATHS = new Set<string>(["/", "/reset-password"]);

const PUBLIC_PREFIXES = [
  "/api/",
  "/_next/",
];

const PUBLIC_STATIC_FILES = new Set<string>([
  "/favicon.ico",
  "/vercel.svg",
  "/file.svg",
  "/globe.svg",
  "/next.svg",
  "/window.svg",
]);

function isPublicPath(pathname: string): boolean {
  if (PUBLIC_PATHS.has(pathname)) return true;
  if (PUBLIC_STATIC_FILES.has(pathname)) return true;
  for (const prefix of PUBLIC_PREFIXES) {
    if (pathname.startsWith(prefix)) return true;
  }
  return false;
}

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Public paths bypass the auth check entirely.
  if (isPublicPath(pathname)) {
    return NextResponse.next();
  }

  // Presence of the HttpOnly refresh cookie is the lightweight signal
  // that the user has (or had) an active session. The backend
  // validates the cookie on /api/v1/auth/refresh — the middleware
  // only needs to block obviously-anonymous requests.
  const refreshCookie = request.cookies.get(REFRESH_COOKIE_NAME);

  if (!refreshCookie || !refreshCookie.value) {
    // Anonymous — redirect to home (which renders the login form via
    // AuthBoundary). Preserve the original path so the client can
    // redirect back after a successful login.
    const loginUrl = new URL("/", request.url);
    loginUrl.searchParams.set("next", pathname);
    return NextResponse.redirect(loginUrl);
  }

  // Cookie present — let the request proceed. If the cookie is
  // expired/revoked, the client-side bootstrap will detect that
  // (silent refresh returns 401) and surface the login screen via
  // AuthBoundary.
  return NextResponse.next();
}

export const config = {
  /**
   * Run on all routes except Next.js internals and static asset
   * extensions. The middleware function itself handles the public/
   * protected decision so we keep the matcher permissive.
   */
  matcher: [
    /*
     * Match all paths except:
     * - /_next/internal (Next.js internals)
     * - /api/* (handled separately above)
     * - static asset file extensions
     */
    "/((?!_next/static|_next/image|.*\\.(?:png|jpg|jpeg|gif|webp|svg|ico|bmp|tiff|css|js|map|woff|woff2|ttf|eot|otf|txt|xml|json|pdf|zip|tar|gz|br|wasm)).*)",
  ],
};
