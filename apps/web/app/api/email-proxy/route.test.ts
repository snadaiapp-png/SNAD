import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
import { POST } from "./route";

function createRequest(
  body: unknown,
  token = "proxy-token",
): NextRequest {
  return new NextRequest("http://localhost/api/email-proxy", {
    method: "POST",
    headers: {
      authorization: `Bearer ${token}`,
      "content-type": "application/json",
    },
    body: JSON.stringify(body),
  });
}

function validPayload() {
  return {
    destination: "user@example.com",
    subject: "Reset your password",
    htmlBody: "<p>Click <a href=\"https://example.com\">here</a></p>",
  };
}

function mockResendSuccess() {
  vi.mocked(fetch).mockResolvedValue(
    new Response(JSON.stringify({ id: "email-1" }), {
      status: 200,
      headers: { "content-type": "application/json" },
    }),
  );
}

function mockResendFailure(status: number) {
  vi.mocked(fetch).mockResolvedValue(
    new Response(JSON.stringify({ error: "rate limited" }), {
      status,
      headers: { "content-type": "application/json" },
    }),
  );
}

function mockResendNetworkError() {
  vi.mocked(fetch).mockRejectedValue(new TypeError("network error"));
}

describe("POST /api/email-proxy", () => {
  beforeEach(() => {
    vi.stubEnv("EMAIL_PROXY_BEARER_TOKEN", "proxy-token");
    vi.stubEnv("RESEND_API_KEY", "resend-runtime-key");
    vi.stubEnv("EMAIL_PROXY_FROM", "security@example.com");
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  // --- Fail-closed: missing env vars (503) ---

  it("returns 503 when EMAIL_PROXY_BEARER_TOKEN is missing", async () => {
    vi.stubEnv("EMAIL_PROXY_BEARER_TOKEN", "");
    const response = await POST(createRequest(validPayload()));
    expect(response.status).toBe(503);
    expect(fetch).not.toHaveBeenCalled();
  });

  it("returns 503 when RESEND_API_KEY is missing", async () => {
    vi.stubEnv("RESEND_API_KEY", "");
    const response = await POST(createRequest(validPayload()));
    expect(response.status).toBe(503);
    expect(fetch).not.toHaveBeenCalled();
  });

  it("returns 503 when EMAIL_PROXY_FROM is missing", async () => {
    vi.stubEnv("EMAIL_PROXY_FROM", "");
    const response = await POST(createRequest(validPayload()));
    expect(response.status).toBe(503);
    expect(fetch).not.toHaveBeenCalled();
  });

  // --- Authentication (401) ---

  it("returns 401 when bearer token is wrong", async () => {
    const response = await POST(createRequest(validPayload(), "wrong-token"));
    expect(response.status).toBe(401);
    expect(await response.json()).toEqual({ error: "Unauthorized" });
    expect(fetch).not.toHaveBeenCalled();
  });

  it("returns 401 when authorization header is missing", async () => {
    const req = new NextRequest("http://localhost/api/email-proxy", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(validPayload()),
    });
    const response = await POST(req);
    expect(response.status).toBe(401);
    expect(fetch).not.toHaveBeenCalled();
  });

  // --- Payload validation (400) ---

  it("returns 400 when JSON is invalid", async () => {
    const req = new NextRequest("http://localhost/api/email-proxy", {
      method: "POST",
      headers: {
        authorization: "Bearer proxy-token",
        "content-type": "application/json",
      },
      body: "{invalid json}",
    });
    const response = await POST(req);
    expect(response.status).toBe(400);
    expect(await response.json()).toEqual({ error: "Invalid JSON payload" });
    expect(fetch).not.toHaveBeenCalled();
  });

  it("returns 400 when payload is missing required fields", async () => {
    const response = await POST(createRequest({ subject: "Reset" }));
    expect(response.status).toBe(400);
    expect(await response.json()).toEqual({ error: "Invalid email payload" });
    expect(fetch).not.toHaveBeenCalled();
  });

  it("returns 400 when htmlBody exceeds 250,000 characters", async () => {
    const response = await POST(
      createRequest({
        ...validPayload(),
        htmlBody: "x".repeat(250_001),
      }),
    );
    expect(response.status).toBe(400);
    expect(fetch).not.toHaveBeenCalled();
  });

  it("returns 400 when subject exceeds 998 characters", async () => {
    const response = await POST(
      createRequest({
        ...validPayload(),
        subject: "x".repeat(999),
      }),
    );
    expect(response.status).toBe(400);
    expect(fetch).not.toHaveBeenCalled();
  });

  it("returns 400 when destination exceeds 320 characters", async () => {
    const response = await POST(
      createRequest({
        ...validPayload(),
        destination: "x".repeat(321),
      }),
    );
    expect(response.status).toBe(400);
    expect(fetch).not.toHaveBeenCalled();
  });

  // --- Resend failures (502) ---

  it("returns 502 when Resend API returns non-2xx", async () => {
    mockResendFailure(429);
    const response = await POST(createRequest(validPayload()));
    expect(response.status).toBe(502);
    expect(await response.json()).toEqual({ error: "Email delivery failed" });
  });

  it("returns 502 when fetch throws a network error", async () => {
    mockResendNetworkError();
    const response = await POST(createRequest(validPayload()));
    expect(response.status).toBe(502);
  });

  // --- Success (200) ---

  it("returns 200 with email id on valid request", async () => {
    mockResendSuccess();
    const response = await POST(createRequest(validPayload()));
    expect(response.status).toBe(200);
    const json = await response.json();
    expect(json).toEqual({ success: true, id: "email-1" });
  });

  it("uses runtime-only credentials for the Resend API call", async () => {
    mockResendSuccess();
    await POST(createRequest(validPayload()));
    expect(fetch).toHaveBeenCalledWith(
      "https://api.resend.com/emails",
      expect.objectContaining({
        method: "POST",
        headers: {
          Authorization: "Bearer resend-runtime-key",
          "Content-Type": "application/json",
        },
      }),
    );
  });

  it("always uses EMAIL_PROXY_FROM as sender, ignoring body.from", async () => {
    mockResendSuccess();
    await POST(
      createRequest({
        ...validPayload(),
        from: "attacker@evil.com",
      }),
    );
    const callArgs = vi.mocked(fetch).mock.calls[0][1] as RequestInit;
    const body = JSON.parse(callArgs.body as string);
    expect(body.from).toBe("security@example.com");
    expect(body.from).not.toBe("attacker@evil.com");
  });

  // --- Security headers ---

  it("includes Cache-Control: no-store on all responses", async () => {
    mockResendSuccess();
    const response = await POST(createRequest(validPayload()));
    expect(response.headers.get("Cache-Control")).toBe("no-store");
    expect(response.headers.get("Pragma")).toBe("no-cache");
  });

  it("includes Cache-Control: no-store on error responses", async () => {
    vi.stubEnv("RESEND_API_KEY", "");
    const response = await POST(createRequest(validPayload()));
    expect(response.headers.get("Cache-Control")).toBe("no-store");
    expect(response.headers.get("Pragma")).toBe("no-cache");
  });

  // --- No response body logging ---

  it("does not log Resend response body on failure", async () => {
    const consoleErrorSpy = vi.spyOn(console, "error");
    mockResendFailure(500);
    await POST(createRequest(validPayload()));
    const loggedArgs = consoleErrorSpy.mock.calls.flat().join(" ");
    // Should NOT contain the actual error body from Resend
    expect(loggedArgs).not.toContain("rate limited");
  });
});
