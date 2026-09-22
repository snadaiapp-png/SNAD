import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
import { POST } from "./route";

function context(...path: string[]) {
  return { params: Promise.resolve({ path }) };
}

function request(
  path: string,
  body?: object,
  cookie?: string,
  sessionScope?: "tenant",
): NextRequest {
  return new NextRequest(`https://snad-app.vercel.app/api/platform${path}`, {
    method: "POST",
    headers: {
      origin: "https://snad-app.vercel.app",
      "content-type": "application/json",
      ...(cookie ? { cookie } : {}),
      ...(sessionScope ? { "x-sanad-session-scope": sessionScope } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
}

describe("platform BFF session hint policy", () => {
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

  it("sets an HttpOnly refresh cookie and a non-sensitive root session hint after login", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ accessToken: "access" }), {
      status: 200,
      headers: {
        "content-type": "application/json",
        "x-sanad-refresh-token": "refresh-secret",
      },
    }));

    const response = await POST(
      request("/api/v1/auth/login", { email: "user@example.com", password: "secret" }),
      context("api", "v1", "auth", "login"),
    );

    const setCookie = response.headers.get("set-cookie") ?? "";
    expect(response.status).toBe(200);
    expect(setCookie).toContain("sanad_refresh=refresh-secret");
    expect(setCookie).toContain("HttpOnly");
    expect(setCookie).toContain("sanad_session_hint=1");
    expect(setCookie).toContain("Path=/");
  });

  it("sets isolated tenant cookies without overwriting control-plane cookies", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ accessToken: "tenant-access" }), {
      status: 200,
      headers: {
        "content-type": "application/json",
        "x-sanad-refresh-token": "tenant-refresh-secret",
      },
    }));

    const response = await POST(
      request(
        "/api/v1/auth/login",
        { email: "tenant@example.com", password: "secret", tenantId: "11111111-1111-1111-1111-111111111111" },
        "sanad_refresh=control-plane-refresh; sanad_session_hint=1",
        "tenant",
      ),
      context("api", "v1", "auth", "login"),
    );

    const setCookie = response.headers.get("set-cookie") ?? "";
    expect(response.status).toBe(200);
    expect(setCookie).toContain("sanad_tenant_refresh=tenant-refresh-secret");
    expect(setCookie).toContain("sanad_tenant_session_hint=1");
    expect(setCookie).not.toContain("sanad_refresh=tenant-refresh-secret");
  });

  it("forwards only the isolated tenant refresh token for tenant-scoped refresh", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ accessToken: "tenant-access" }), {
      status: 200,
      headers: { "content-type": "application/json" },
    }));

    await POST(
      request(
        "/api/v1/auth/refresh",
        undefined,
        "sanad_refresh=control-plane-refresh; sanad_tenant_refresh=tenant-refresh",
        "tenant",
      ),
      context("api", "v1", "auth", "refresh"),
    );

    const upstreamInit = vi.mocked(fetch).mock.calls[0]?.[1];
    const upstreamHeaders = new Headers(upstreamInit?.headers);
    expect(upstreamHeaders.get("x-sanad-refresh-token")).toBe("tenant-refresh");
  });

  it("clears both cookies when refresh credentials are rejected", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ error: "Unauthorized" }), {
      status: 401,
      headers: { "content-type": "application/json" },
    }));

    const response = await POST(
      request("/api/v1/auth/refresh", undefined, "sanad_refresh=stale; sanad_session_hint=1"),
      context("api", "v1", "auth", "refresh"),
    );

    const setCookie = response.headers.get("set-cookie") ?? "";
    expect(response.status).toBe(401);
    expect(setCookie).toContain("sanad_refresh=");
    expect(setCookie).toContain("sanad_session_hint=");
    expect(setCookie).toContain("Max-Age=0");
  });

  it("clears only tenant-session cookies when isolated refresh credentials are rejected", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ error: "Unauthorized" }), {
      status: 401,
      headers: { "content-type": "application/json" },
    }));

    const response = await POST(
      request(
        "/api/v1/auth/refresh",
        undefined,
        "sanad_refresh=control-plane; sanad_session_hint=1; sanad_tenant_refresh=stale; sanad_tenant_session_hint=1",
        "tenant",
      ),
      context("api", "v1", "auth", "refresh"),
    );

    const setCookie = response.headers.get("set-cookie") ?? "";
    expect(response.status).toBe(401);
    expect(setCookie).toContain("sanad_tenant_refresh=");
    expect(setCookie).toContain("sanad_tenant_session_hint=");
    expect(setCookie).not.toContain("sanad_refresh=");
    expect(setCookie).not.toContain("sanad_session_hint=");
  });

  it("clears the local session hint even when upstream logout is unavailable", async () => {
    vi.mocked(fetch).mockRejectedValue(new Error("offline"));

    const response = await POST(
      request("/api/v1/auth/logout", undefined, "sanad_refresh=active; sanad_session_hint=1"),
      context("api", "v1", "auth", "logout"),
    );

    const setCookie = response.headers.get("set-cookie") ?? "";
    expect(response.status).toBe(502);
    expect(setCookie).toContain("sanad_session_hint=");
    expect(setCookie).toContain("Max-Age=0");
  });
});
