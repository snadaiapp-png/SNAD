import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiClient, ApiClientCancellation } from "./client";
import {
  ApiHttpError,
  ApiNetworkError,
  ApiRequestSerializationError,
  ApiResponseParseError,
  ApiTimeoutError,
} from "./errors";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

function jsonResponse(body: unknown, status = 200, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json", ...headers },
  });
}

describe("ApiClient", () => {
  const client = () => new ApiClient({ baseUrl: "https://api.example.com", timeoutMs: 1000 });

  it("performs a typed GET with encoded query parameters", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: "1" }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(client().get<{ id: string }>("/api/v1/items", {
      query: { tenantId: "a b", active: false },
    })).resolves.toEqual({ id: "1" });
    expect(fetchMock.mock.calls[0][0]).toBe("https://api.example.com/api/v1/items?tenantId=a+b&active=false");
  });

  it("serializes POST bodies and sets JSON content type", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: "1" }, 201));
    vi.stubGlobal("fetch", fetchMock);
    await client().post("/api/v1/items", { name: "SNAD" });
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.body).toBe(JSON.stringify({ name: "SNAD" }));
    expect(init.headers).toMatchObject({ Accept: "application/json", "Content-Type": "application/json" });
  });

  it("does not permit caller authorization header injection", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }));
    vi.stubGlobal("fetch", fetchMock);
    await client().get("/api/v1/items", { context: { headers: { Authorization: "not-used", "X-Trace": "trace-1" } } });
    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>;
    expect(headers.Authorization).toBeUndefined();
    expect(headers["X-Trace"]).toBe("trace-1");
  });

  it("returns undefined for 204", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    await expect(client().delete("/api/v1/items/1")).resolves.toBeUndefined();
  });

  it("normalizes HTTP errors", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({ message: "duplicate", path: "/api/v1/items" }, 409, { "x-request-id": "req-1" })));
    try {
      await client().post("/api/v1/items", { name: "duplicate" });
      throw new Error("expected request to fail");
    } catch (error) {
      expect(error).toBeInstanceOf(ApiHttpError);
      const http = error as ApiHttpError;
      expect(http.status).toBe(409);
      expect(http.backendMessage).toBe("duplicate");
      expect(http.requestId).toBe("req-1");
    }
  });

  it("classifies malformed successful JSON", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("not-json", { status: 200, headers: { "content-type": "application/json" } })));
    await expect(client().get("/api/v1/items")).rejects.toBeInstanceOf(ApiResponseParseError);
  });

  it("classifies network failures", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("connection failed")));
    await expect(client().get("/api/v1/items")).rejects.toBeInstanceOf(ApiNetworkError);
  });

  it("classifies an internal timeout separately", async () => {
    vi.useFakeTimers();
    vi.stubGlobal("fetch", vi.fn((_url: string, init: RequestInit) => new Promise((_resolve, reject) => {
      init.signal?.addEventListener("abort", () => reject(init.signal?.reason));
    })));
    const assertion = expect(client().get("/api/v1/items", { timeoutMs: 25 })).rejects.toBeInstanceOf(ApiTimeoutError);
    await vi.advanceTimersByTimeAsync(30);
    await assertion;
  });

  it("classifies external cancellation separately", async () => {
    const controller = new AbortController();
    controller.abort(new DOMException("cancelled", "AbortError"));
    vi.stubGlobal("fetch", vi.fn());
    await expect(client().get("/api/v1/items", { signal: controller.signal })).rejects.toBeInstanceOf(ApiClientCancellation);
    expect(fetch).not.toHaveBeenCalled();
  });

  it("rejects a circular request body before fetch", async () => {
    const body: Record<string, unknown> = {};
    body.self = body;
    vi.stubGlobal("fetch", vi.fn());
    await expect(client().post("/api/v1/items", body)).rejects.toBeInstanceOf(ApiRequestSerializationError);
    expect(fetch).not.toHaveBeenCalled();
  });
});

describe("X-SNAD-If-Match transport header", () => {
  it("rewrites If-Match to X-SNAD-If-Match for same-origin BFF", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: "addr-1" }));
    vi.stubGlobal("fetch", fetchMock);
    const bffClient = new ApiClient({ baseUrl: "/api/platform", timeoutMs: 5000 });
    await bffClient.patch("/api/v2/crm/addresses/addr-1", { line1: "Updated" }, {
      context: { headers: { "If-Match": '"addr-v1-abc12345"' } },
    });
    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>;
    expect(headers["X-SNAD-If-Match"]).toBe('"addr-v1-abc12345"');
    expect(headers["If-Match"]).toBeUndefined();
  });

  it("does not include browser-facing If-Match on same-origin request", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: "addr-1" }));
    vi.stubGlobal("fetch", fetchMock);
    const bffClient = new ApiClient({ baseUrl: "/api/platform", timeoutMs: 5000 });
    await bffClient.patch("/api/v2/crm/addresses/addr-1", { line1: "Updated" }, {
      context: { headers: { "If-Match": '"addr-v1-abc12345"' } },
    });
    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>;
    // The key insight: "If-Match" must not be present in the browser-facing
    // request headers so Vercel cannot intercept it.
    expect(Object.keys(headers).some((k) => k.toLowerCase() === "if-match")).toBe(false);
  });

  it("preserves standard If-Match for direct backend URL", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: "addr-1" }));
    vi.stubGlobal("fetch", fetchMock);
    const directClient = new ApiClient({ baseUrl: "https://localhost:8080", timeoutMs: 5000 });
    await directClient.patch("/api/v2/crm/addresses/addr-1", { line1: "Updated" }, {
      context: { headers: { "If-Match": '"addr-v1-abc12345"' } },
    });
    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>;
    expect(headers["If-Match"]).toBe('"addr-v1-abc12345"');
    expect(headers["X-SNAD-If-Match"]).toBeUndefined();
  });

  it("applies translation on retry after token refresh", async () => {
    let callCount = 0;
    const fetchMock = vi.fn().mockImplementation(() => {
      callCount += 1;
      if (callCount === 1) {
        return new Response(null, { status: 401 });
      }
      return jsonResponse({ id: "addr-1" });
    });
    vi.stubGlobal("fetch", fetchMock);

    const bffClient = new ApiClient({ baseUrl: "/api/platform", timeoutMs: 5000 });
    let refreshCalled = false;
    bffClient.setUnauthorizedHandler(async () => { refreshCalled = true; return true; });

    await bffClient.patch("/api/v2/crm/addresses/addr-1", { line1: "Updated" }, {
      context: { headers: { "If-Match": '"addr-v1-abc12345"' } },
    });

    expect(refreshCalled).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    // Both the original and retry requests must use X-SNAD-If-Match
    const retryHeaders = fetchMock.mock.calls[1][1].headers as Record<string, string>;
    expect(retryHeaders["X-SNAD-If-Match"]).toBe('"addr-v1-abc12345"');
    expect(retryHeaders["If-Match"]).toBeUndefined();
  });

  it("does not rewrite when no If-Match is provided", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: "addr-1" }));
    vi.stubGlobal("fetch", fetchMock);
    const bffClient = new ApiClient({ baseUrl: "/api/platform", timeoutMs: 5000 });
    await bffClient.get("/api/v2/crm/addresses/addr-1");
    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>;
    expect(headers["X-SNAD-If-Match"]).toBeUndefined();
    expect(headers["If-Match"]).toBeUndefined();
  });
});
