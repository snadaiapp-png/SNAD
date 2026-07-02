import { afterEach, describe, expect, it, vi } from "vitest";

describe("API configuration", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.resetModules();
  });

  it("normalizes a trailing slash outside production", async () => {
    vi.stubEnv("NODE_ENV", "test");
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://api.example.com///");
    const config = await import("./config");
    expect(config.API_BASE_URL).toBe("https://api.example.com");
  });

  it("uses the same-origin BFF in production", async () => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://backend.example.com");
    const config = await import("./config");
    expect(config.API_BASE_URL).toBe("/api/platform");
  });

  it("builds encoded query parameters", async () => {
    const { buildUrl } = await import("./config");
    expect(buildUrl("https://api.example.com", "/api/v1/items", {
      tenantId: "a b",
      active: false,
      page: 2,
      ignored: undefined,
      tag: ["a", "b"],
    })).toBe("https://api.example.com/api/v1/items?tenantId=a+b&active=false&page=2&tag=a&tag=b");
  });

  it("builds a same-origin BFF URL", async () => {
    const { buildUrl } = await import("./config");
    expect(buildUrl("/api/platform", "/api/v1/auth/me")).toBe("/api/platform/api/v1/auth/me");
  });

  it("rejects an empty base URL", async () => {
    const { validateBaseUrl } = await import("./config");
    expect(() => validateBaseUrl("")).toThrowError(/not set/i);
  });

  it("rejects unsafe relative URLs", async () => {
    const { validateBaseUrl } = await import("./config");
    expect(() => validateBaseUrl("//evil.example.com/api")).toThrowError(/safe same-origin/i);
    expect(() => validateBaseUrl("/control-plane")).toThrowError(/under \/api\//i);
  });

  it("rejects non-local HTTP", async () => {
    const { validateBaseUrl } = await import("./config");
    expect(() => validateBaseUrl("http://api.example.com")).toThrowError(/https/i);
  });

  it("permits local HTTP", async () => {
    const { validateBaseUrl } = await import("./config");
    expect(() => validateBaseUrl("http://localhost:8080")).not.toThrow();
  });
});
