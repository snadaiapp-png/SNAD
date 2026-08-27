import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
import { POST } from "./route";

function context(...path: string[]) {
  return { params: Promise.resolve({ path }) };
}

describe("platform BFF proxy-origin validation", () => {
  beforeEach(() => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("BACKEND_API_BASE_URL", "https://sanad-backend.example.com");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ accessToken: "access", expiresAt: "2030-01-01T00:00:00Z" }), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    ));
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("accepts the browser public origin when Next.js is reached through the preview reverse proxy", async () => {
    const request = new NextRequest("http://127.0.0.1:3000/api/platform/api/v1/auth/login", {
      method: "POST",
      headers: {
        origin: "https://preview.trycloudflare.com",
        "x-forwarded-host": "preview.trycloudflare.com",
        "x-forwarded-proto": "https",
        "content-type": "application/json",
      },
      body: JSON.stringify({ email: "admin@example.com", password: "secret" }),
    });

    const response = await POST(
      request,
      context("api", "v1", "auth", "login"),
    );

    expect(response.status).toBe(200);
    expect(fetch).toHaveBeenCalledTimes(1);
  });
});
