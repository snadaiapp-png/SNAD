/**
 * SANAD — DEFECT-019: middleware route protection tests
 * ------------------------------------------------------------
 * Verifies that the middleware:
 *  - Allows public paths through (no redirect)
 *  - Redirects anonymous requests on protected paths to /
 *  - Preserves the original path in the ?next= query param
 *  - Allows protected requests through when the refresh cookie is present
 *
 * The middleware is loaded dynamically to avoid Next.js config coupling.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock next/server's NextResponse and NextRequest just enough to drive
// the middleware without spinning up a real HTTP server.
type CookieValue = { value: string } | undefined;

class MockNextRequest {
  readonly url: string;
  readonly cookies: { get: (name: string) => CookieValue };
  readonly nextUrl: { pathname: string };

  constructor(url: string, cookies: Record<string, string> = {}) {
    this.url = url;
    const parsed = new URL(url);
    this.nextUrl = { pathname: parsed.pathname };
    this.cookies = {
      get: (name: string) => (cookies[name] ? { value: cookies[name] } : undefined),
    };
  }
}

// Re-export NextResponse shapes we need to assert on.
const NextResponseMock = {
  next: vi.fn(() => ({ type: "next" })),
  redirect: vi.fn((url: URL) => ({ type: "redirect", url: url.toString() })),
};

vi.mock("next/server", () => ({
  NextResponse: NextResponseMock,
  // Type-only export — not used at runtime in tests.
  NextRequest: MockNextRequest,
}));

// Import the middleware under test AFTER mocks are in place.
// Vitest hoists vi.mock calls so the import will see the mocks.
const { middleware } = await import("./middleware");

// The real NextRequest type is complex; the mock satisfies the subset
// the middleware actually uses. Cast through unknown to satisfy TS.
type NextRequestLike = unknown;

function asNextRequest(req: MockNextRequest): NextRequestLike {
  return req as unknown as NextRequestLike;
}

describe("DEFECT-019: middleware route protection", () => {
  beforeEach(() => {
    NextResponseMock.next.mockClear();
    NextResponseMock.redirect.mockClear();
  });

  it("allows the home page (public) without redirect", () => {
    const req = new MockNextRequest("https://snad-app.vercel.app/");
    middleware(asNextRequest(req));
    expect(NextResponseMock.next).toHaveBeenCalledTimes(1);
    expect(NextResponseMock.redirect).not.toHaveBeenCalled();
  });

  it("allows /reset-password (public) without redirect", () => {
    const req = new MockNextRequest("https://snad-app.vercel.app/reset-password");
    middleware(asNextRequest(req));
    expect(NextResponseMock.next).toHaveBeenCalledTimes(1);
    expect(NextResponseMock.redirect).not.toHaveBeenCalled();
  });

  it("allows /api/* routes (public) without redirect", () => {
    const req = new MockNextRequest("https://snad-app.vercel.app/api/system/backend-status");
    middleware(asNextRequest(req));
    expect(NextResponseMock.next).toHaveBeenCalledTimes(1);
    expect(NextResponseMock.redirect).not.toHaveBeenCalled();
  });

  it("allows /_next/* routes (public) without redirect", () => {
    const req = new MockNextRequest("https://snad-app.vercel.app/_next/static/chunk.js");
    middleware(asNextRequest(req));
    expect(NextResponseMock.next).toHaveBeenCalledTimes(1);
    expect(NextResponseMock.redirect).not.toHaveBeenCalled();
  });

  it("redirects anonymous requests on protected path /dashboard to / with ?next=/dashboard", () => {
    const req = new MockNextRequest("https://snad-app.vercel.app/dashboard");
    middleware(asNextRequest(req));
    expect(NextResponseMock.redirect).toHaveBeenCalledTimes(1);
    const redirectedUrl = NextResponseMock.redirect.mock.calls[0][0] as URL;
    expect(redirectedUrl.pathname).toBe("/");
    expect(redirectedUrl.searchParams.get("next")).toBe("/dashboard");
    expect(NextResponseMock.next).not.toHaveBeenCalled();
  });

  it("allows protected path through when sanad_refresh cookie is present", () => {
    const req = new MockNextRequest(
      "https://snad-app.vercel.app/dashboard",
      { sanad_refresh: "fake-refresh-token" },
    );
    middleware(asNextRequest(req));
    expect(NextResponseMock.next).toHaveBeenCalledTimes(1);
    expect(NextResponseMock.redirect).not.toHaveBeenCalled();
  });

  it("redirects when sanad_refresh cookie is empty string", () => {
    const req = new MockNextRequest(
      "https://snad-app.vercel.app/dashboard",
      { sanad_refresh: "" },
    );
    middleware(asNextRequest(req));
    expect(NextResponseMock.redirect).toHaveBeenCalledTimes(1);
    expect(NextResponseMock.next).not.toHaveBeenCalled();
  });

  it("allows favicon.ico (public static file) without redirect", () => {
    const req = new MockNextRequest("https://snad-app.vercel.app/favicon.ico");
    middleware(asNextRequest(req));
    expect(NextResponseMock.next).toHaveBeenCalledTimes(1);
    expect(NextResponseMock.redirect).not.toHaveBeenCalled();
  });

  it("redirects to / preserving multi-segment original path", () => {
    const req = new MockNextRequest("https://snad-app.vercel.app/orgs/123/members");
    middleware(asNextRequest(req));
    expect(NextResponseMock.redirect).toHaveBeenCalledTimes(1);
    const redirectedUrl = NextResponseMock.redirect.mock.calls[0][0] as URL;
    expect(redirectedUrl.searchParams.get("next")).toBe("/orgs/123/members");
  });
});
