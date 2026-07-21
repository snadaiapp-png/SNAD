import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
import { GET } from "./route";

function context(...path: string[]) {
  return { params: Promise.resolve({ path }) };
}

function request(): NextRequest {
  return new NextRequest("https://snad-app.vercel.app/api/platform/api/v1/auth/me");
}

describe("Render-only Vercel production policy", () => {
  beforeEach(() => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("VERCEL_ENV", "production");
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("rejects a non-Render backend before any upstream request", async () => {
    vi.stubEnv("BACKEND_API_BASE_URL", "https://streak-train-empower.ngrok-free.dev");
    vi.spyOn(console, "error").mockImplementation(() => undefined);

    const response = await GET(request(), context("api", "v1", "auth", "me"));

    expect(response.status).toBe(503);
    expect(fetch).not.toHaveBeenCalled();
  });

  it("allows the approved Render backend", async () => {
    vi.stubEnv("BACKEND_API_BASE_URL", "https://sanad-backend-mcrj.onrender.com");
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 401 }));

    const response = await GET(request(), context("api", "v1", "auth", "me"));

    expect(response.status).toBe(401);
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(vi.mocked(fetch).mock.calls[0][0]).toBe(
      "https://sanad-backend-mcrj.onrender.com/api/v1/auth/me",
    );
  });
});
