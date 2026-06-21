import { describe, it, expect, vi, afterEach } from "vitest";

vi.mock("@/lib/api", () => ({ checkBackendIntegration: vi.fn() }));
const { GET } = await import("./route");
const { checkBackendIntegration } = await import("@/lib/api");

describe("GET /api/system/backend-status", () => {
  afterEach(() => vi.restoreAllMocks());

  it("returns the safe healthy contract", async () => {
    vi.mocked(checkBackendIntegration).mockResolvedValue({
      configured: true,
      reachable: true,
      statusCode: 200,
      error: null,
    });
    const response = await GET();
    expect(await response.json()).toEqual({ configured: true, reachable: true, statusCode: 200 });
  });

  it("returns the safe unconfigured contract", async () => {
    vi.mocked(checkBackendIntegration).mockResolvedValue({
      configured: false,
      reachable: false,
      statusCode: null,
      error: "internal detail",
    });
    const response = await GET();
    const body = await response.json();
    expect(body).toEqual({ configured: false, reachable: false, statusCode: null });
    expect(body).not.toHaveProperty("error");
  });

  it("never exposes internal fields", async () => {
    vi.mocked(checkBackendIntegration).mockResolvedValue({
      configured: true,
      reachable: false,
      statusCode: null,
      error: "connection refused",
    });
    const body = await (await GET()).json();
    expect(Object.keys(body).sort()).toEqual(["configured", "reachable", "statusCode"]);
  });
});
