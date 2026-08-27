import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
import { maxDuration, POST } from "./route";

function context(...path: string[]) {
  return { params: Promise.resolve({ path }) };
}

function loginRequest(): NextRequest {
  return new NextRequest("https://snad-app.vercel.app/api/platform/api/v1/auth/login", {
    method: "POST",
    headers: {
      origin: "https://snad-app.vercel.app",
      "content-type": "application/json",
    },
    body: JSON.stringify({ email: "admin@example.com", password: "secret" }),
  });
}

describe("platform BFF cold-start budget", () => {
  beforeEach(() => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("VERCEL_ENV", "production");
    vi.stubEnv("BACKEND_REQUEST_TIMEOUT_MS", "");
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("keeps login alive long enough for the observed Render cold start", async () => {
    const timeoutSpy = vi.spyOn(AbortSignal, "timeout").mockImplementation(() => new AbortController().signal);
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 401 })));

    const response = await POST(
      loginRequest(),
      context("api", "v1", "auth", "login"),
    );

    expect(response.status).toBe(401);
    expect(timeoutSpy).toHaveBeenCalledTimes(1);
    expect(timeoutSpy.mock.calls[0][0]).toBeGreaterThanOrEqual(120_000);
    expect(maxDuration).toBeGreaterThanOrEqual(140);
  });
});
