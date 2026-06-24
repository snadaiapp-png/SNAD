import { describe, it, expect, vi, afterEach } from "vitest";

describe("legacy API compatibility", () => {
  const originalEnv = process.env.NEXT_PUBLIC_API_BASE_URL;

  afterEach(() => {
    if (originalEnv === undefined) delete process.env.NEXT_PUBLIC_API_BASE_URL;
    else process.env.NEXT_PUBLIC_API_BASE_URL = originalEnv;
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.resetModules();
  });

  it("re-exports normalized API configuration", async () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = "https://api.example.com/";
    const config = await import("./api-config");
    expect(config.API_BASE_URL).toBe("https://api.example.com");
    expect(config.IS_API_CONFIGURED).toBe(true);
    expect(config.API_TIMEOUT_MS).toBe(60000);
  });

  it("keeps buildApiUrl compatible", async () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = "https://api.example.com";
    const config = await import("./api-config");
    expect(config.buildApiUrl("actuator/health")).toBe("https://api.example.com/actuator/health");
  });

  it("keeps the integration health check compatible", async () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = "https://api.example.com";
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 200 })));
    const integration = await import("./api-integration");
    const result = await integration.checkBackendIntegration();
    expect(result).toMatchObject({ configured: true, reachable: true, statusCode: 200 });
  });

  it("keeps unconfigured integration behavior compatible", async () => {
    delete process.env.NEXT_PUBLIC_API_BASE_URL;
    const integration = await import("./api-integration");
    const result = await integration.checkBackendIntegration();
    expect(result).toMatchObject({ configured: false, reachable: false, statusCode: null });
  });
});
