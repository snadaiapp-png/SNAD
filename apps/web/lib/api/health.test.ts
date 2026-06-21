import { describe, expect, it, vi } from "vitest";
import { ApiClient } from "./client";
import { ApiHttpError, ApiNetworkError } from "./errors";
import { checkBackendIntegration } from "./health";

describe("checkBackendIntegration", () => {
  it("reports an unconfigured client safely", async () => {
    const result = await checkBackendIntegration(new ApiClient({ baseUrl: "" }));
    expect(result).toEqual({
      configured: false,
      reachable: false,
      statusCode: null,
      error: "NEXT_PUBLIC_API_BASE_URL is not set",
    });
  });

  it("reports a healthy backend", async () => {
    const client = new ApiClient({ baseUrl: "https://api.example.com" });
    vi.spyOn(client, "get").mockResolvedValue({ status: "UP" });
    await expect(checkBackendIntegration(client)).resolves.toEqual({
      configured: true,
      reachable: true,
      statusCode: 200,
      error: null,
    });
  });

  it("preserves an HTTP health status without exposing a body", async () => {
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
  });

  it("reports network failure as unreachable", async () => {
    const client = new ApiClient({ baseUrl: "https://api.example.com" });
    vi.spyOn(client, "get").mockRejectedValue(new ApiNetworkError("offline"));
    const result = await checkBackendIntegration(client);
    expect(result).toMatchObject({ configured: true, reachable: false, statusCode: null });
  });
});
