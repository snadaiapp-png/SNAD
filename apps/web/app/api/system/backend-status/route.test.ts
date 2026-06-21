import { describe, it, expect, vi, afterEach } from "vitest";

vi.mock("@/lib/api", () => ({ checkBackendIntegration: vi.fn() }));
const { GET } = await import("./route");
const { checkBackendIntegration } = await import("@/lib/api");

describe("GET /api/system/backend-status", () => {
  afterEach(() => vi.restoreAllMocks());

  it("returns the safe healthy contract with targetHost and checkedAt", async () => {
    vi.mocked(checkBackendIntegration).mockResolvedValue({
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

  it("returns the safe unconfigured contract with null targetHost", async () => {
    vi.mocked(checkBackendIntegration).mockResolvedValue({
      configured: false,
      reachable: false,
      statusCode: null,
      targetHost: null,
      checkedAt: "2026-06-21T11:23:26.000Z",
      error: "internal detail",
    });
    const response = await GET();
    const body = await response.json();
    expect(body).toEqual({
      configured: false,
      reachable: false,
      statusCode: null,
      targetHost: null,
      checkedAt: "2026-06-21T11:23:26.000Z",
    });
    expect(body).not.toHaveProperty("error");
  });

  it("returns reachable=false with statusCode=503 when backend is suspended", async () => {
    vi.mocked(checkBackendIntegration).mockResolvedValue({
      configured: true,
      reachable: false,
      statusCode: 503,
      targetHost: "sanad-backend.onrender.com",
      checkedAt: "2026-06-21T11:23:26.000Z",
      error: "Service Suspended",
    });
    const response = await GET();
    const body = await response.json();
    expect(body.reachable).toBe(false);
    expect(body.statusCode).toBe(503);
    expect(body.targetHost).toBe("sanad-backend.onrender.com");
  });

  it("never exposes the internal error field", async () => {
    vi.mocked(checkBackendIntegration).mockResolvedValue({
      configured: true,
      reachable: false,
      statusCode: null,
      targetHost: "api.example.com",
      checkedAt: "2026-06-21T11:23:26.000Z",
      error: "connection refused — sensitive detail",
    });
    const body = await (await GET()).json();
    expect(Object.keys(body).sort()).toEqual([
      "checkedAt",
      "configured",
      "reachable",
      "statusCode",
      "targetHost",
    ]);
    expect(body).not.toHaveProperty("error");
    expect(JSON.stringify(body)).not.toContain("sensitive detail");
  });

  it("sets Cache-Control: no-store on the response", async () => {
    vi.mocked(checkBackendIntegration).mockResolvedValue({
      configured: true,
      reachable: true,
      statusCode: 200,
      targetHost: "api.example.com",
      checkedAt: "2026-06-21T11:23:26.000Z",
      error: null,
    });
    const response = await GET();
    expect(response.headers.get("Cache-Control")).toContain("no-store");
    expect(response.headers.get("Pragma")).toBe("no-cache");
  });

  it("returns checkedAt as a current UTC ISO timestamp", async () => {
    const before = new Date().toISOString();
    vi.mocked(checkBackendIntegration).mockResolvedValue({
      configured: true,
      reachable: true,
      statusCode: 200,
      targetHost: "api.example.com",
      checkedAt: before,
      error: null,
    });
    const body = await (await GET()).json();
    // checkedAt must be a valid ISO date string
    const parsed = new Date(body.checkedAt);
    expect(parsed.toString()).not.toBe("Invalid Date");
    // Must be close to "now" (within 5 seconds of the mocked value)
    const after = new Date().toISOString();
    expect(body.checkedAt >= before).toBe(true);
    expect(body.checkedAt <= after).toBe(true);
  });

  it("does not expose credentials in targetHost", async () => {
    vi.mocked(checkBackendIntegration).mockResolvedValue({
      configured: true,
      reachable: true,
      statusCode: 200,
      targetHost: "api.example.com",
      checkedAt: "2026-06-21T11:23:26.000Z",
      error: null,
    });
    const body = await (await GET()).json();
    expect(body.targetHost).not.toContain(":");
    expect(body.targetHost).not.toContain("@");
    expect(body.targetHost).not.toContain("://");
    expect(body.targetHost).not.toMatch(/password|secret|token/i);
  });
});
