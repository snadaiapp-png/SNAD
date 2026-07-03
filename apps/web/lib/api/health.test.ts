import { describe, expect, it, vi } from "vitest";
import { ApiClient } from "./client";
import { ApiHttpError, ApiNetworkError } from "./errors";
import { checkBackendIntegration } from "./health";

describe("checkBackendIntegration", () => {
  it("reports an unconfigured client safely with null targetHost", async () => {
    const result = await checkBackendIntegration(new ApiClient({ baseUrl: "" }));
    expect(result).toEqual({
      configured: false,
      reachable: false,
      statusCode: null,
      targetHost: null,
      checkedAt: expect.any(String),
      error: "NEXT_PUBLIC_API_BASE_URL is not set",
    });
    // checkedAt must be a valid ISO timestamp
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

  it("uses cache: no-store for the health request", async () => {
    const client = new ApiClient({ baseUrl: "https://api.example.com" });
    const spy = vi.spyOn(client, "get").mockResolvedValue({ status: "UP" });
    await checkBackendIntegration(client);
    expect(spy).toHaveBeenCalledWith("/actuator/health", expect.objectContaining({ cache: "no-store" }));
  });

  it("extracts hostname without scheme, path, or credentials", async () => {
    const client = new ApiClient({ baseUrl: "https://sanad-backend-mcrj.onrender.com" });
    vi.spyOn(client, "get").mockResolvedValue({ status: "UP" });
    const result = await checkBackendIntegration(client);
    expect(result.targetHost).toBe("sanad-backend-mcrj.onrender.com");
    // Must not contain scheme
    expect(result.targetHost).not.toContain("://");
    // Must not contain path
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
});
