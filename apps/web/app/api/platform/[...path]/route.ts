import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const REFRESH_COOKIE = "sanad_refresh";
const REFRESH_HEADER = "x-sanad-refresh-token";
const AUTH_PATH_PREFIX = "/api/v1/auth";
const REFRESH_PATH = "/api/v1/auth/refresh";
const LOGOUT_PATH = "/api/v1/auth/logout";
const COOKIE_MAX_AGE_SECONDS = 7 * 24 * 60 * 60;
// Keep total retries below Vercel function limits. Long backend cold-start waits
// otherwise surface as platform 502s before this route can return a safe body.
const REQUEST_TIMEOUT_MS = 4_000;
const RETRY_DELAY_MS = 250;
const MAX_IDEMPOTENT_ATTEMPTS = 2;

const FORWARDED_REQUEST_HEADERS = [
  "accept",
  "accept-language",
  "authorization",
  "content-type",
  "idempotency-key",
  "x-correlation-id",
  "x-request-id",
] as const;

function jsonError(message: string, status: number): NextResponse {
  return NextResponse.json(
    { error: message },
    {
      status,
      headers: {
        "Cache-Control": "no-store",
        Pragma: "no-cache",
      },
    },
  );
}

function backendBaseUrl(): string | null {
  const raw = process.env.BACKEND_API_BASE_URL || process.env.NEXT_PUBLIC_API_BASE_URL || "";
  if (!raw || raw.startsWith("/")) return null;

  try {
    const parsed = new URL(raw);
    const isLocal = ["localhost", "127.0.0.1", "::1"].includes(parsed.hostname);
    if (parsed.protocol !== "https:" && !(isLocal && parsed.protocol === "http:")) return null;
    if (parsed.username || parsed.password) return null;
    parsed.pathname = parsed.pathname.replace(/\/+$/, "");
    parsed.search = "";
    parsed.hash = "";
    return parsed.toString().replace(/\/$/, "");
  } catch {
    return null;
  }
}

function backendPath(path: string[]): string | null {
  if (!path.length) return null;
  const encoded = path.map((segment) => encodeURIComponent(segment)).join("/");
  const value = `/${encoded}`;
  return value === "/api/v1" || value.startsWith("/api/v1/") ? value : null;
}

function isStateChanging(method: string): boolean {
  return ["POST", "PUT", "PATCH", "DELETE"].includes(method);
}

function isIdempotent(method: string): boolean {
  return method === "GET" || method === "HEAD";
}

function hasValidOrigin(request: NextRequest): boolean {
  const origin = request.headers.get("origin");
  return !origin || origin === request.nextUrl.origin;
}

function requestHeaders(request: NextRequest, path: string, baseUrl: string): Headers {
  const headers = new Headers();
  for (const name of FORWARDED_REQUEST_HEADERS) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }

  // ngrok free-tier returns ERR_NGROK_6024 (browser warning page) for any request
  // that looks like it came from a browser. The BFF is a server-side fetch, but
  // ngrok's heuristic still matches because of the default User-Agent. Adding
  // this header bypasses the warning. We only add it when the backend is an
  // ngrok host so we don't pollute requests to other backends.
  if (baseUrl.includes("ngrok")) {
    headers.set("ngrok-skip-browser-warning", "any-value");
  }

  if (path === REFRESH_PATH) {
    const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;
    if (refreshToken) headers.set(REFRESH_HEADER, refreshToken);
  }

  return headers;
}

function responseHeaders(upstream: Response): Headers {
  const headers = new Headers({
    "Cache-Control": "no-store",
    Pragma: "no-cache",
  });

  for (const name of ["content-type", "x-correlation-id", "x-request-id"]) {
    const value = upstream.headers.get(name);
    if (value) headers.set(name, value);
  }

  return headers;
}

function persistRefreshToken(response: NextResponse, upstream: Response, path: string): void {
  const refreshToken = upstream.headers.get(REFRESH_HEADER);
  if (refreshToken && path.startsWith(AUTH_PATH_PREFIX)) {
    response.cookies.set({
      name: REFRESH_COOKIE,
      value: refreshToken,
      httpOnly: true,
      secure: process.env.NODE_ENV === "production",
      sameSite: "strict",
      path: AUTH_PATH_PREFIX.replace("/api/v1/auth", "/api/platform/api/v1/auth"),
      maxAge: COOKIE_MAX_AGE_SECONDS,
    });
  }

  if (path === LOGOUT_PATH) {
    response.cookies.set({
      name: REFRESH_COOKIE,
      value: "",
      httpOnly: true,
      secure: process.env.NODE_ENV === "production",
      sameSite: "strict",
      path: "/api/platform/api/v1/auth",
      maxAge: 0,
    });
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function fetchBackend(
  target: string,
  request: NextRequest,
  headers: Headers,
  body: ArrayBuffer | undefined,
): Promise<Response> {
  const attempts = isIdempotent(request.method) ? MAX_IDEMPOTENT_ATTEMPTS : 1;
  let lastError: unknown;

  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const upstream = await fetch(target, {
        method: request.method,
        headers,
        body: body && body.byteLength > 0 ? body : undefined,
        cache: "no-store",
        redirect: "manual",
        signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
      });

      if (upstream.status < 500 || attempt === attempts) return upstream;
      await upstream.body?.cancel().catch(() => undefined);
    } catch (error) {
      lastError = error;
      if (attempt === attempts) throw error;
    }

    await sleep(RETRY_DELAY_MS);
  }

  throw lastError instanceof Error ? lastError : new Error("Backend fetch failed");
}

type RouteContext = { params: Promise<{ path: string[] }> };

async function proxy(request: NextRequest, context: RouteContext): Promise<NextResponse> {
  if (isStateChanging(request.method) && !hasValidOrigin(request)) {
    return jsonError("Forbidden", 403);
  }

  const baseUrl = backendBaseUrl();
  if (!baseUrl) {
    console.error("Platform BFF backend URL is not configured");
    return jsonError("Service unavailable", 503);
  }

  const params = await context.params;
  const path = backendPath(params.path);
  if (!path) return jsonError("Not found", 404);

  const target = `${baseUrl}${path}${request.nextUrl.search}`;
  const headers = requestHeaders(request, path, baseUrl);
  const supportsBody = !["GET", "HEAD"].includes(request.method);
  const body = supportsBody ? await request.arrayBuffer() : undefined;

  try {
    const upstream = await fetchBackend(target, request, headers, body);

    const response = new NextResponse(
      upstream.status === 204 || upstream.status === 304 ? null : upstream.body,
      {
        status: upstream.status,
        headers: responseHeaders(upstream),
      },
    );
    persistRefreshToken(response, upstream, path);
    return response;
  } catch (error) {
    console.error("Platform BFF request failed", {
      errorName: error instanceof Error ? error.name : "UnknownError",
      path,
    });
    return jsonError("Backend unavailable", 502);
  }
}

export async function GET(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export async function POST(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export async function PUT(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export async function PATCH(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export async function DELETE(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}