import { describe, it, expect, vi, afterEach } from "vitest";

vi.mock("@/lib/api", () => ({ checkBackendIntegration: vi.fn() }));
const { GET } = await import("./route");
const { checkBackendIntegration } = await import("@/lib/api");

describe("GET /api/system/backend-status", () => {
  afterEach(() => vi.restoreAllMocks());

  it("returns the minimal healthy contract without exposing targetHost", async () => {
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
      checkedAt: "2026-06-21T11:23:26.000Z",
    });
    // Critical: targetHost must NOT be present in the public response.
    expect(body).not.toHaveProperty("targetHost");
  });

  it("returns the minimal unconfigured contract without exposing targetHost", async () => {
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
      checkedAt: "2026-06-21T11:23:26.000Z",
    });
    expect(body).not.toHaveProperty("error");
    expect(body).not.toHaveProperty("targetHost");
  });

  it("returns reachable=false with statusCode=503 when backend is suspended, without host leak", async () => {
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
    expect(body).not.toHaveProperty("targetHost");
  });

  it("never exposes the internal error field or targetHost", async () => {
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
    ]);
    expect(body).not.toHaveProperty("error");
    expect(body).not.toHaveProperty("targetHost");
    expect(JSON.stringify(body)).not.toContain("sensitive detail");
    expect(JSON.stringify(body)).not.toContain("api.example.com");
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

  it("does not leak any host, tunnel, ngrok, or backend URL hint", async () => {
    vi.mocked(checkBackendIntegration).mockResolvedValue({
      configured: true,
      reachable: true,
      statusCode: 200,
      targetHost: "sanad-backend-mcrj.onrender.com",
      checkedAt: "2026-06-21T11:23:26.000Z",
      error: null,
    });
    const body = await (await GET()).json();
    const serialized = JSON.stringify(body);
    // The body must not contain the backend host, tunnel provider, or any URL
    // indicator. Note: we check against the actual targetHost value, not the
    // generic ":" character (which appears in every JSON serialization).
    expect(serialized).not.toContain("ngrok");
    expect(serialized).not.toContain("streak-train");
    expect(serialized).not.toContain("onrender");
    expect(serialized).not.toContain("empower");
    expect(serialized).not.toContain(".dev");
    expect(serialized).not.toMatch(/password|secret|token/i);
    expect(serialized).not.toMatch(/host|tunnel/i);
    // The response must not carry a targetHost field at all.
    expect(body).not.toHaveProperty("targetHost");
  });
});
