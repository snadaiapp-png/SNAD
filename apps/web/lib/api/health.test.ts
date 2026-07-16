import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiClient } from "./client";
import { ApiHttpError, ApiNetworkError } from "./errors";
import { checkBackendIntegration } from "./health";

describe("checkBackendIntegration", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  it("reports an unconfigured client safely with null targetHost", async () => {
    const result = await checkBackendIntegration(new ApiClient({ baseUrl: "" }));
    expect(result).toEqual({
      configured: false,
      reachable: false,
      statusCode: null,
      targetHost: null,
      checkedAt: expect.any(String),
      error: "Backend API base URL is not set",
    });
    expect(new Date(result.checkedAt).toString()).not.toBe("Invalid Date");
  });

  it("reports a healthy backend with targetHost and checkedAt", async () => {
    const client = new ApiClient({ baseUrl: "https://api.example.com" });
    vi.spyOn(client, "get").mockResolvedValue({ status: "UP" });
    const result = await checkBackendIntegration(client);
    expect(result).toEqual({
      configured: true,
      reachable: true,
      statusCode: 200,
      targetHost: "api.example.com",
      checkedAt: expect.any(String),
      error: null,
    });
    expect(new Date(result.checkedAt).toString()).not.toBe("Invalid Date");
  });

  it("does not report a 200 response as healthy when Actuator status is not UP", async () => {
    const client = new ApiClient({ baseUrl: "https://api.example.com" });
    vi.spyOn(client, "get").mockResolvedValue({ status: "DOWN" });
    const result = await checkBackendIntegration(client);
    expect(result).toMatchObject({
      configured: true,
      reachable: false,
      statusCode: 200,
      targetHost: "api.example.com",
      error: "Backend health check failed (status: DOWN)",
    });
  });

  it("uses the private server backend URL when the production client uses the same-origin BFF", async () => {
    vi.stubEnv("BACKEND_API_BASE_URL", "https://sanad-backend-mcrj.onrender.com/");
    const fetchMock = vi.fn().mockResolvedValue({
      status: 200,
      json: vi.fn().mockResolvedValue({ status: "UP" }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const result = await checkBackendIntegration(new ApiClient({ baseUrl: "/api/platform" }));

    expect(fetchMock).toHaveBeenCalledWith(
      "https://sanad-backend-mcrj.onrender.com/actuator/health",
      expect.objectContaining({
        method: "GET",
        cache: "no-store",
        headers: { Accept: "application/json" },
      }),
    );
    expect(result).toEqual({
      configured: true,
      reachable: true,
      statusCode: 200,
      targetHost: "sanad-backend-mcrj.onrender.com",
      checkedAt: expect.any(String),
      error: null,
    });
  });

  it("preserves an HTTP health status without exposing a body (503 → reachable=false)", async () => {
    const client = new ApiClient({ baseUrl: "https://api.example.com" });
    vi.spyOn(client, "get").mockRejectedValue(new ApiHttpError("unhealthy", {
      status: 503,
      error: "Unavailable",
      message: null,
      path: "/actuator/health",
      requestId: null,
      body: null,
    }));
    const result = await checkBackendIntegration(client);
    expect(result.statusCode).toBe(503);
    expect(result.reachable).toBe(false);
    expect(result.targetHost).toBe("api.example.com");
    expect(result.checkedAt).toBeDefined();
  });

  it("reports network failure as unreachable with targetHost preserved", async () => {
    const client = new ApiClient({ baseUrl: "https://api.example.com" });
    vi.spyOn(client, "get").mockRejectedValue(new ApiNetworkError("offline"));
    const result = await checkBackendIntegration(client);
    expect(result).toMatchObject({
      configured: true,
      reachable: false,
      statusCode: null,
      targetHost: "api.example.com",
    });
    expect(result.checkedAt).toBeDefined();
  });

  it("uses cache: no-store and the tunneled-backend timeout for the health request", async () => {
    const client = new ApiClient({ baseUrl: "https://api.example.com" });
    const spy = vi.spyOn(client, "get").mockResolvedValue({ status: "UP" });
    await checkBackendIntegration(client);
    expect(spy).toHaveBeenCalledWith(
      "/actuator/health",
      expect.objectContaining({ cache: "no-store", timeoutMs: 15_000 }),
    );
  });

  it("honors a bounded backend timeout override", async () => {
    vi.stubEnv("BACKEND_REQUEST_TIMEOUT_MS", "20000");
    const client = new ApiClient({ baseUrl: "https://api.example.com" });
    const spy = vi.spyOn(client, "get").mockResolvedValue({ status: "UP" });
    await checkBackendIntegration(client);
    expect(spy).toHaveBeenCalledWith(
      "/actuator/health",
      expect.objectContaining({ timeoutMs: 20_000 }),
    );
  });

  it("extracts hostname without scheme, path, or credentials", async () => {
    const client = new ApiClient({ baseUrl: "https://sanad-backend-mcrj.onrender.com" });
    vi.spyOn(client, "get").mockResolvedValue({ status: "UP" });
    const result = await checkBackendIntegration(client);
    expect(result.targetHost).toBe("sanad-backend-mcrj.onrender.com");
    expect(result.targetHost).not.toContain("://");
    expect(result.targetHost).not.toContain("/");
  });

  it("includes non-standard port in targetHost", async () => {
    const client = new ApiClient({ baseUrl: "https://api.example.com:8443" });
    vi.spyOn(client, "get").mockResolvedValue({ status: "UP" });
    const result = await checkBackendIntegration(client);
    expect(result.targetHost).toBe("api.example.com:8443");
  });

  it("omits standard port 443 from targetHost", async () => {
    const client = new ApiClient({ baseUrl: "https://api.example.com:443" });
    vi.spyOn(client, "get").mockResolvedValue({ status: "UP" });
    const result = await checkBackendIntegration(client);
    expect(result.targetHost).toBe("api.example.com");
  });

  it("checkedAt is current (within 2 seconds of now)", async () => {
    const before = Date.now();
    const client = new ApiClient({ baseUrl: "https://api.example.com" });
    vi.spyOn(client, "get").mockResolvedValue({ status: "UP" });
    const result = await checkBackendIntegration(client);
    const after = Date.now();
    const checkedAtMs = new Date(result.checkedAt).getTime();
    expect(checkedAtMs).toBeGreaterThanOrEqual(before);
    expect(checkedAtMs).toBeLessThanOrEqual(after);
  });

  it("BFF mode: reports reachable=true when /api/v1/auth/me returns 401", async () => {
    const client = new ApiClient({ baseUrl: "/api/platform" });
    vi.spyOn(client, "get").mockRejectedValue(new ApiHttpError("unauthorized", {
      status: 401,
      error: "Unauthorized",
      message: "Authentication required",
      path: "/api/v1/auth/me",
      requestId: null,
      body: null,
    }));
    const result = await checkBackendIntegration(client);
    expect(result).toMatchObject({
      configured: true,
      reachable: true,
      statusCode: 401,
      targetHost: null,
    });
  });

  it("BFF mode: reports reachable=false on network error", async () => {
    const client = new ApiClient({ baseUrl: "/api/platform" });
    vi.spyOn(client, "get").mockRejectedValue(new ApiNetworkError("offline"));
    const result = await checkBackendIntegration(client);
    expect(result).toMatchObject({
      configured: true,
      reachable: false,
      statusCode: null,
      targetHost: null,
    });
  });

  it("BFF mode: reports reachable=false on 5xx error", async () => {
    const client = new ApiClient({ baseUrl: "/api/platform" });
    vi.spyOn(client, "get").mockRejectedValue(new ApiHttpError("server error", {
      status: 502,
      error: "Bad Gateway",
      message: null,
      path: "/api/v1/auth/me",
      requestId: null,
      body: null,
    }));
    const result = await checkBackendIntegration(client);
    expect(result).toMatchObject({
      configured: true,
      reachable: false,
      statusCode: 502,
    });
  });
});
