import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
import { GET, PATCH, POST, DELETE } from "./route";

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

describe("BFF header contract", () => {
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

  // ---------------------------------------------------------------------------
  // Response propagation
  // ---------------------------------------------------------------------------

  describe("response ETag propagation", () => {
    it("forwards upstream ETag unchanged on HTTP 201", async () => {
      vi.mocked(fetch).mockResolvedValue(
        new Response(JSON.stringify({ id: "addr-1", version: 1 }), {
          status: 201,
          headers: {
            "content-type": "application/json",
            etag: '"account-addr-1-v1-a1b2c3d4"',
          },
        }),
      );

      const response = await POST(
        request("/api/v2/crm/accounts/acc-1/addresses", {
          method: "POST",
          headers: { authorization: "Bearer token" },
          body: { addressType: "OFFICE" },
        }),
        context("api", "v2", "crm", "accounts", "acc-1", "addresses"),
      );

      expect(response.status).toBe(201);
      expect(response.headers.get("etag")).toBe('"account-addr-1-v1-a1b2c3d4"');
    });

    it("forwards upstream ETag unchanged on HTTP 200", async () => {
      vi.mocked(fetch).mockResolvedValue(
        new Response(JSON.stringify({ id: "addr-1", version: 2 }), {
          status: 200,
          headers: {
            "content-type": "application/json",
            etag: '"account-addr-1-v2-e5f6g7h8"',
          },
        }),
      );

      const response = await PATCH(
        request("/api/v2/crm/addresses/addr-1", {
          method: "PATCH",
          headers: {
            authorization: "Bearer token",
            "if-match": '"account-addr-1-v1-a1b2c3d4"',
          },
          body: { line1: "Updated" },
        }),
        context("api", "v2", "crm", "addresses", "addr-1"),
      );

      expect(response.status).toBe(200);
      expect(response.headers.get("etag")).toBe('"account-addr-1-v2-e5f6g7h8"');
    });

    it("preserves weak ETag validators from upstream", async () => {
      vi.mocked(fetch).mockResolvedValue(
        new Response(JSON.stringify({ id: "addr-1" }), {
          status: 200,
          headers: {
            "content-type": "application/json",
            etag: 'W/"12"',
          },
        }),
      );

      const response = await GET(
        request("/api/v2/crm/addresses/addr-1", {
          headers: { authorization: "Bearer token" },
        }),
        context("api", "v2", "crm", "addresses", "addr-1"),
      );

      expect(response.status).toBe(200);
      expect(response.headers.get("etag")).toBe('W/"12"');
    });

    it("does not create an empty ETag header when upstream omits it", async () => {
      vi.mocked(fetch).mockResolvedValue(
        new Response(JSON.stringify({ id: "addr-1" }), {
          status: 200,
          headers: { "content-type": "application/json" },
        }),
      );

      const response = await GET(
        request("/api/v2/crm/addresses/addr-1", {
          headers: { authorization: "Bearer token" },
        }),
        context("api", "v2", "crm", "addresses", "addr-1"),
      );

      expect(response.status).toBe(200);
      expect(response.headers.get("etag")).toBeNull();
    });

    it("retains existing cache-control and correlation headers alongside ETag", async () => {
      vi.mocked(fetch).mockResolvedValue(
        new Response(JSON.stringify({ id: "addr-1" }), {
          status: 200,
          headers: {
            "content-type": "application/json",
            etag: '"addr-1-v1-abc12345"',
            "x-correlation-id": "trace-xyz",
          },
        }),
      );

      const response = await GET(
        request("/api/v2/crm/addresses/addr-1", {
          headers: { authorization: "Bearer token" },
        }),
        context("api", "v2", "crm", "addresses", "addr-1"),
      );

      expect(response.headers.get("etag")).toBe('"addr-1-v1-abc12345"');
      expect(response.headers.get("x-correlation-id")).toBe("trace-xyz");
      expect(response.headers.get("cache-control")).toBe("no-store");
    });
  });

  // ---------------------------------------------------------------------------
  // Request propagation
  // ---------------------------------------------------------------------------

  describe("request If-Match propagation", () => {
    it("forwards incoming If-Match to the upstream request", async () => {
      vi.mocked(fetch).mockResolvedValue(
        new Response(JSON.stringify({ id: "addr-1" }), {
          status: 200,
          headers: { "content-type": "application/json" },
        }),
      );

      await PATCH(
        request("/api/v2/crm/addresses/addr-1", {
          method: "PATCH",
          headers: {
            authorization: "Bearer token",
            "if-match": '"account-addr-1-v1-a1b2c3d4"',
          },
          body: { line1: "Updated" },
        }),
        context("api", "v2", "crm", "addresses", "addr-1"),
      );

      const [, init] = vi.mocked(fetch).mock.calls[0];
      const headers = init?.headers as Headers;
      expect(headers.get("if-match")).toBe('"account-addr-1-v1-a1b2c3d4"');
    });

    it("preserves quoted If-Match values", async () => {
      vi.mocked(fetch).mockResolvedValue(
        new Response(null, { status: 412 }),
      );

      await PATCH(
        request("/api/v2/crm/addresses/addr-1", {
          method: "PATCH",
          headers: {
            authorization: "Bearer token",
            "if-match": '"stale-addr-1-v0-00000000"',
          },
          body: { line1: "Stale" },
        }),
        context("api", "v2", "crm", "addresses", "addr-1"),
      );

      const [, init] = vi.mocked(fetch).mock.calls[0];
      const headers = init?.headers as Headers;
      expect(headers.get("if-match")).toBe('"stale-addr-1-v0-00000000"');
    });

    it("preserves weak ETag If-Match values", async () => {
      vi.mocked(fetch).mockResolvedValue(
        new Response(JSON.stringify({ id: "addr-1" }), {
          status: 200,
          headers: { "content-type": "application/json" },
        }),
      );

      await PATCH(
        request("/api/v2/crm/addresses/addr-1", {
          method: "PATCH",
          headers: {
            authorization: "Bearer token",
            "if-match": 'W/"12"',
          },
          body: { line1: "Updated" },
        }),
        context("api", "v2", "crm", "addresses", "addr-1"),
      );

      const [, init] = vi.mocked(fetch).mock.calls[0];
      const headers = init?.headers as Headers;
      expect(headers.get("if-match")).toBe('W/"12"');
    });

    it("forwards If-Match for DELETE requests", async () => {
      vi.mocked(fetch).mockResolvedValue(
        new Response(null, { status: 204 }),
      );

      await DELETE(
        request("/api/v2/crm/addresses/addr-1", {
          method: "DELETE",
          headers: {
            authorization: "Bearer token",
            "if-match": '"account-addr-1-v1-a1b2c3d4"',
          },
        }),
        context("api", "v2", "crm", "addresses", "addr-1"),
      );

      const [, init] = vi.mocked(fetch).mock.calls[0];
      const headers = init?.headers as Headers;
      expect(headers.get("if-match")).toBe('"account-addr-1-v1-a1b2c3d4"');
    });

    it("does not synthesize an absent If-Match header", async () => {
      vi.mocked(fetch).mockResolvedValue(
        new Response(JSON.stringify({ id: "addr-1" }), {
          status: 200,
          headers: { "content-type": "application/json" },
        }),
      );

      await PATCH(
        request("/api/v2/crm/addresses/addr-1", {
          method: "PATCH",
          headers: { authorization: "Bearer token" },
          body: { line1: "No precondition" },
        }),
        context("api", "v2", "crm", "addresses", "addr-1"),
      );

      const [, init] = vi.mocked(fetch).mock.calls[0];
      const headers = init?.headers as Headers;
      expect(headers.get("if-match")).toBeNull();
    });
  });

  // ---------------------------------------------------------------------------
  // Security regression
  // ---------------------------------------------------------------------------

  describe("header security regression", () => {
    it("does not forward unapproved request headers", async () => {
      vi.mocked(fetch).mockResolvedValue(
        new Response(JSON.stringify({}), {
          status: 200,
          headers: { "content-type": "application/json" },
        }),
      );

      await GET(
        request("/api/v1/auth/me", {
          headers: {
            authorization: "Bearer token",
            "x-internal-debug": "true",
            "x-backend-secret": "leaked",
          },
        }),
        context("api", "v1", "auth", "me"),
      );

      const [, init] = vi.mocked(fetch).mock.calls[0];
      const headers = init?.headers as Headers;
      expect(headers.get("x-internal-debug")).toBeNull();
      expect(headers.get("x-backend-secret")).toBeNull();
    });

    it("does not forward unapproved upstream response headers", async () => {
      vi.mocked(fetch).mockResolvedValue(
        new Response(JSON.stringify({}), {
          status: 200,
          headers: {
            "content-type": "application/json",
            etag: '"test-v1-12345678"',
            server: "nginx/1.25",
            "x-upstream-id": "internal-42",
          },
        }),
      );

      const response = await GET(
        request("/api/v1/auth/me", {
          headers: { authorization: "Bearer token" },
        }),
        context("api", "v1", "auth", "me"),
      );

      expect(response.headers.get("etag")).toBe('"test-v1-12345678"');
      expect(response.headers.get("server")).toBeNull();
      expect(response.headers.get("x-upstream-id")).toBeNull();
    });

    it("does not blindly proxy Set-Cookie from upstream", async () => {
      vi.mocked(fetch).mockResolvedValue(
        new Response(JSON.stringify({}), {
          status: 200,
          headers: {
            "content-type": "application/json",
            "set-cookie": "session=abc123; Path=/; HttpOnly",
          },
        }),
      );

      const response = await GET(
        request("/api/v1/auth/me", {
          headers: { authorization: "Bearer token" },
        }),
        context("api", "v1", "auth", "me"),
      );

      // The BFF sets its own cookies for auth paths but does not proxy
      // upstream Set-Cookie for non-auth endpoints
      const setCookie = response.headers.get("set-cookie") ?? "";
      expect(setCookie).not.toContain("session=abc123");
    });
  });
});
