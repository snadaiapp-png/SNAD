import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
import { GET } from "./route";

function context(...path: string[]) {
  return { params: Promise.resolve({ path }) };
}

function request(path: string, headers: Record<string, string> = {}): NextRequest {
  return new NextRequest(`https://snad-app.vercel.app/api/platform${path}`, { headers });
}

describe("platform BFF API v2 forwarding", () => {
  beforeEach(() => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("BACKEND_API_BASE_URL", "https://sanad-backend.example.com");
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("forwards CRM v2 requests with authorization and query parameters", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ data: [] }), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );

    const accountId = "11111111-1111-4111-8111-111111111111";
    const response = await GET(
      request(`/api/v2/crm/accounts/${accountId}/addresses?limit=1`, {
        authorization: "Bearer crm-access-token",
      }),
      context("api", "v2", "crm", "accounts", accountId, "addresses"),
    );

    expect(response.status).toBe(200);
    const [url, init] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe(`https://sanad-backend.example.com/api/v2/crm/accounts/${accountId}/addresses?limit=1`);
    expect((init?.headers as Headers).get("authorization")).toBe("Bearer crm-access-token");
  });

  it("continues to reject unsupported API versions", async () => {
    const response = await GET(
      request("/api/v3/crm/accounts"),
      context("api", "v3", "crm", "accounts"),
    );

    expect(response.status).toBe(404);
    expect(fetch).not.toHaveBeenCalled();
  });
});
