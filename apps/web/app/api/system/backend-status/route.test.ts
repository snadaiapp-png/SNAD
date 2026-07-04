import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const apiMocks = vi.hoisted(() => ({
  checkBackendIntegration: vi.fn(),
  ApiClient: vi.fn(function MockApiClient(options: unknown) {
    return { options };
  }),
}));

vi.mock("@/lib/api", () => apiMocks);

const { GET } = await import("./route");

describe("GET /api/system/backend-status", () => {
  beforeEach(() => {
    vi.stubEnv("BACKEND_API_BASE_URL", "https://sanad-backend-mcrj.onrender.com");
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://fallback.example.com");
    apiMocks.ApiClient.mockClear();
    apiMocks.checkBackendIntegration.mockReset();
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
  });

  it("uses the server-only backend URL instead of the relative production BFF URL", async () => {
    apiMocks.checkBackendIntegration.mockResolvedValue({
      configured: true,
      reachable: true,
      statusCode: 200,
      targetHost: "sanad-backend-mcrj.onrender.com",
      checkedAt: "2026-06-21T11:23:26.000Z",
      error: null,
    });

    await GET();

    expect(apiMocks.ApiClient).toHaveBeenCalledWith({
      baseUrl: "https://sanad-backend-mcrj.onrender.com",
      timeoutMs: 10_000,
    });
    expect(apiMocks.checkBackendIntegration).toHaveBeenCalledWith(
      expect.objectContaining({
        options: {
          baseUrl: "https://sanad-backend-mcrj.onrender.com",
          timeoutMs: 10_000,
        },
      }),
    );
  });

  it("falls back to NEXT_PUBLIC_API_BASE_URL when the server-only URL is absent", async () => {
    vi.stubEnv("BACKEND_API_BASE_URL", "");
    apiMocks.checkBackendIntegration.mockResolvedValue({
      configured: true,
      reachable: true,
      statusCode: 200,
      targetHost: "fallback.example.com",
      checkedAt: "2026-06-21T11:23:26.000Z",
      error: null,
    });

    await GET();

    expect(apiMocks.ApiClient).toHaveBeenCalledWith({
      baseUrl: "https://fallback.example.com",
      timeoutMs: 10_000,
    });
  });

  it("returns the safe healthy contract with targetHost and checkedAt", async () => {
    apiMocks.checkBackendIntegration.mockResolvedValue({
      configured: true,
      reachable: true,
      statusCode: 200,
      targetHost: "sanad-backend-mcrj.onrender.com",
      checkedAt: "2026-06-21T11:23:26.000Z",
      error: null,
    });

    const response = await GET();
    const body = await response.json();

    expect(body).toEqual({
      configured: true,
      reachable: true,
      statusCode: 200,
      targetHost: "sanad-backend-mcrj.onrender.com",
      checkedAt: "2026-06-21T11:23:26.000Z",
    });
  });

  it("returns the safe unconfigured contract without exposing internal details", async () => {
    vi.stubEnv("BACKEND_API_BASE_URL", "");
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "");
    apiMocks.checkBackendIntegration.mockResolvedValue({
      configured: false,
      reachable: false,
      statusCode: null,
      targetHost: null,
      checkedAt: "2026-06-21T11:23:26.000Z",
      error: "internal detail",
    });

    const body = await (await GET()).json();

    expect(body).toEqual({
      configured: false,
      reachable: false,
      statusCode: null,
      targetHost: null,
      checkedAt: "2026-06-21T11:23:26.000Z",
    });
    expect(body).not.toHaveProperty("error");
  });

  it("returns reachable=false with the upstream status code", async () => {
    apiMocks.checkBackendIntegration.mockResolvedValue({
      configured: true,
      reachable: false,
      statusCode: 503,
      targetHost: "sanad-backend-mcrj.onrender.com",
      checkedAt: "2026-06-21T11:23:26.000Z",
      error: "Service unavailable",
    });

    const body = await (await GET()).json();

    expect(body.reachable).toBe(false);
    expect(body.statusCode).toBe(503);
    expect(body.targetHost).toBe("sanad-backend-mcrj.onrender.com");
    expect(body).not.toHaveProperty("error");
  });

  it("sets no-store response headers", async () => {
    apiMocks.checkBackendIntegration.mockResolvedValue({
      configured: true,
      reachable: true,
      statusCode: 200,
      targetHost: "api.example.com",
      checkedAt: new Date().toISOString(),
      error: null,
    });

    const response = await GET();

    expect(response.headers.get("Cache-Control")).toContain("no-store");
    expect(response.headers.get("Pragma")).toBe("no-cache");
  });

  it("never exposes credentials or URLs in targetHost", async () => {
    apiMocks.checkBackendIntegration.mockResolvedValue({
      configured: true,
      reachable: true,
      statusCode: 200,
      targetHost: "api.example.com",
      checkedAt: new Date().toISOString(),
      error: null,
    });

    const body = await (await GET()).json();

    expect(body.targetHost).not.toContain(":");
    expect(body.targetHost).not.toContain("@");
    expect(body.targetHost).not.toContain("://");
    expect(body.targetHost).not.toMatch(/password|secret|token/i);
  });
});
