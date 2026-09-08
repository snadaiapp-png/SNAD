import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
import { GET } from "./route";

/**
 * R0C-12 corrective recertification — BFF access identity (mission §11).
 *
 * The browser reaches the backend executive APIs through the Vercel BFF
 * (`/api/platform/:path*`). These tests pin the equivalent-path contract
 * for `/access-check/v2`:
 *
 *   BACKEND_DIRECT           — GET {backend}/api/v1/executive/access-check/v2
 *   VERCEL_BFF_EQUIVALENT    — GET {web}/api/platform/api/v1/executive/access-check/v2
 *
 * must produce the IDENTICAL access identity: the BFF forwards the
 * `authorization` header (FORWARDED_REQUEST_HEADERS allow-list) and streams
 * the backend's identity body unchanged. No tokens are exposed by the test.
 */

const IDENTITY_BODY = JSON.stringify({
  authenticated: true,
  capabilities: {
    "subscription.read": true,
    "catalog.read": true,
    "application.read": true,
    "plan.read": true,
    "pricing.read": true,
    "entitlement.read": true,
    "usage.read": true,
    "billing.read": true,
    "provisioning.read": true,
    "audit.read": true,
    "catalog.manage": false,
  },
});

function context(...path: string[]) {
  return { params: Promise.resolve({ path }) };
}

function request(path: string, headers: Record<string, string> = {}): NextRequest {
  return new NextRequest(`https://snad-app.vercel.app/api/platform${path}`, {
    method: "GET",
    headers: new Headers(headers),
  });
}

describe("platform BFF — access-check identity propagation", () => {
  beforeEach(() => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("BACKEND_API_BASE_URL", "https://sanad-backend.example.com");
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("VERCEL_BFF_EQUIVALENT: forwards authorization upstream and streams the identical identity body", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(IDENTITY_BODY, {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    const response = await GET(
      request("/api/v1/executive/access-check/v2", {
        authorization: "Bearer test-access-token",
        accept: "application/json",
      }),
      context("api", "v1", "executive", "access-check", "v2"),
    );

    // Upstream call target — exact backend-direct equivalent path.
    expect(fetch).toHaveBeenCalledTimes(1);
    const [upstreamUrl, upstreamInit] = vi.mocked(fetch).mock.calls[0] as unknown as [
      string,
      RequestInit,
    ];
    expect(upstreamUrl).toBe(
      "https://sanad-backend.example.com/api/v1/executive/access-check/v2",
    );

    // Identity propagation — the JWT must reach the backend untouched.
    const forwardedHeaders = new Headers(upstreamInit?.headers);
    expect(forwardedHeaders.get("authorization")).toBe("Bearer test-access-token");

    // Identical identity body — BFF must not alter the access decision.
    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual(JSON.parse(IDENTITY_BODY));
  });

  it("fails closed: a BFF request without authorization still proxies, and the backend identity governs the decision", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(
        JSON.stringify({ authenticated: false, capabilities: {} }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );

    const response = await GET(
      request("/api/v1/executive/access-check/v2"),
      context("api", "v1", "executive", "access-check", "v2"),
    );

    const [, upstreamInit] = vi.mocked(fetch).mock.calls[0] as unknown as [
      string,
      RequestInit,
    ];
    const forwardedHeaders = new Headers(upstreamInit?.headers);
    expect(forwardedHeaders.get("authorization")).toBeNull();
    await expect(response.json()).resolves.toEqual({
      authenticated: false,
      capabilities: {},
    });
  });
});
