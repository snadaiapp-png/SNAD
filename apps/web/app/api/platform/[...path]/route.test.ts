import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
import { GET, POST } from "./route";

function context(...path: string[]) {
  return { params: Promise.resolve({ path }) };
}

function request(
  path: string,
  options: {
    method?: string;
    body?: unknown;
    headers?: Record<string, string>;
  } = {},
): NextRequest {
  const method = options.method ?? "GET";
  const headers = new Headers(options.headers);
  const body = options.body === undefined ? undefined : JSON.stringify(options.body);
  if (body && !headers.has("content-type")) headers.set("content-type", "application/json");

  return new NextRequest(`https://snad-app.vercel.app/api/platform${path}`, {
    method,
    headers,
    body,
  });
}

describe("platform BFF", () => {
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

  it("fails closed when the backend URL is missing", async () => {
    vi.stubEnv("BACKEND_API_BASE_URL", "");
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "");

    const response = await GET(
      request("/api/v1/auth/me"),
      context("api", "v1", "auth", "me"),
    );

    expect(response.status).toBe(503);
    expect(fetch).not.toHaveBeenCalled();
  });

  it("rejects paths outside the backend API namespace", async () => {
    const response = await GET(
      request("/actuator/env"),
      context("actuator", "env"),
    );

    expect(response.status).toBe(404);
    expect(fetch).not.toHaveBeenCalled();
  });

  it("rejects cross-origin state-changing requests", async () => {
    const response = await POST(
      request("/api/v1/auth/login", {
        method: "POST",
        headers: { origin: "https://evil.example.com" },
        body: { email: "admin@example.com", password: "secret" },
      }),
      context("api", "v1", "auth", "login"),
    );

    expect(response.status).toBe(403);
    expect(fetch).not.toHaveBeenCalled();
  });

  it("stores a rotated refresh token in an HttpOnly first-party cookie", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ accessToken: "access", expiresAt: "2030-01-01T00:00:00Z" }), {
        status: 200,
        headers: {
          "content-type": "application/json",
          "x-sanad-refresh-token": "refresh-secret",
        },
      }),
    );

    const response = await POST(
      request("/api/v1/auth/login", {
        method: "POST",
        headers: { origin: "https://snad-app.vercel.app" },
        body: { email: "admin@example.com", password: "secret" },
      }),
      context("api", "v1", "auth", "login"),
    );

    expect(response.status).toBe(200);
    expect(response.headers.get("x-sanad-refresh-token")).toBeNull();
    const setCookie = response.headers.get("set-cookie") ?? "";
    expect(setCookie).toContain("sanad_refresh=refresh-secret");
    expect(setCookie).toContain("HttpOnly");
    expect(setCookie).toContain("Secure");
    expect(setCookie).toContain("SameSite=strict");
    expect(setCookie).toContain("Path=/api/platform/api/v1/auth");
  });

  it("injects the first-party refresh cookie into the trusted backend header", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ accessToken: "new-access" }), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );

    await POST(
      request("/api/v1/auth/refresh", {
        method: "POST",
        headers: {
          origin: "https://snad-app.vercel.app",
          cookie: "sanad_refresh=refresh-from-cookie",
        },
      }),
      context("api", "v1", "auth", "refresh"),
    );

    const [, init] = vi.mocked(fetch).mock.calls[0];
    const headers = init?.headers as Headers;
    expect(headers.get("x-sanad-refresh-token")).toBe("refresh-from-cookie");
    expect(headers.get("cookie")).toBeNull();
  });

  it("forwards access authorization to protected control-plane endpoints", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ totalTenants: 1 }), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );

    const response = await GET(
      request("/api/v1/control-plane/dashboard", {
        headers: { authorization: "Bearer access-token" },
      }),
      context("api", "v1", "control-plane", "dashboard"),
    );

    expect(response.status).toBe(200);
    const [url, init] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("https://sanad-backend.example.com/api/v1/control-plane/dashboard");
    expect((init?.headers as Headers).get("authorization")).toBe("Bearer access-token");
  });

  it("clears the refresh cookie on logout", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }));

    const response = await POST(
      request("/api/v1/auth/logout", {
        method: "POST",
        headers: {
          origin: "https://snad-app.vercel.app",
          authorization: "Bearer access-token",
          cookie: "sanad_refresh=refresh-from-cookie",
        },
      }),
      context("api", "v1", "auth", "logout"),
    );

    expect(response.status).toBe(204);
    const setCookie = response.headers.get("set-cookie") ?? "";
    expect(setCookie).toContain("sanad_refresh=");
    expect(setCookie).toContain("Max-Age=0");
  });

  it("returns a generic 502 without leaking backend details", async () => {
    vi.mocked(fetch).mockRejectedValue(new Error("database password=do-not-leak"));
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);

    const response = await GET(
      request("/api/v1/control-plane/dashboard"),
      context("api", "v1", "control-plane", "dashboard"),
    );

    expect(response.status).toBe(502);
    expect(await response.json()).toEqual({ error: "Backend unavailable" });
    expect(JSON.stringify(consoleError.mock.calls)).not.toContain("do-not-leak");
  });
});
