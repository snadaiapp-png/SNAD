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
    await client().post("/api/v1/items", { name: "SANAD" });
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.body).toBe(JSON.stringify({ name: "SANAD" }));
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
