import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const REFRESH_COOKIE = "sanad_refresh";
const SESSION_HINT_COOKIE = "sanad_session_hint";
const REFRESH_HEADER = "x-sanad-refresh-token";
const AUTH_PATH_PREFIX = "/api/v1/auth";
const LOGIN_PATH = "/api/v1/auth/login";
const REFRESH_PATH = "/api/v1/auth/refresh";
const LOGOUT_PATH = "/api/v1/auth/logout";
const CHANGE_CREDENTIAL_PATH = "/api/v1/auth/change-credential";
const COOKIE_MAX_AGE_SECONDS = 7 * 24 * 60 * 60;
const PRODUCTION_BACKEND_URL = "https://sanad-backend-mcrj.onrender.com";
const PRODUCTION_BACKEND_HOST = new URL(PRODUCTION_BACKEND_URL).hostname;

// BACKEND_REQUEST_TIMEOUT_MS is an end-to-end BFF budget, not a per-attempt timeout.
// It bounds retries inside the serverless execution window and preserves deterministic failures.
const DEFAULT_REQUEST_TIMEOUT_MS = 15_000;
const MIN_REQUEST_TIMEOUT_MS = 2_500;
const MAX_REQUEST_TIMEOUT_MS = 25_000;
const MIN_ATTEMPT_TIMEOUT_MS = 1_000;
const RETRY_DELAY_MS = 250;
const MAX_IDEMPOTENT_ATTEMPTS = 2;
const RETRYABLE_UPSTREAM_STATUSES = new Set([502, 503, 504]);

const FORWARDED_REQUEST_HEADERS = [
  "accept",
  "accept-language",
  "authorization",
  "content-type",
  "idempotency-key",
  "x-correlation-id",
  "x-request-id",
] as const;

type BackendFailureKind = "timeout" | "network";
type BackendResult = { response: Response; attempts: number };

class BackendRequestError extends Error {
  constructor(
    readonly kind: BackendFailureKind,
    readonly attempts: number,
  ) {
    super(kind === "timeout" ? "Backend request timed out" : "Backend request failed");
    this.name = "BackendRequestError";
  }
}

function requestId(request: NextRequest): string {
  return request.headers.get("x-request-id")
    || request.headers.get("x-correlation-id")
    || globalThis.crypto.randomUUID();
}

function jsonError(
  message: string,
  status: number,
  id: string,
  failureKind?: BackendFailureKind,
): NextResponse {
  const headers = new Headers({
    "Cache-Control": "no-store",
    Pragma: "no-cache",
    Vary: "Cookie",
    "x-request-id": id,
  });
  if (failureKind) {
    headers.set("x-sanad-bff-error", failureKind === "timeout" ? "upstream-timeout" : "upstream-network");
    headers.set("Retry-After", "1");
  }
  return NextResponse.json({ error: message }, { status, headers });
}

function backendBaseUrl(): string | null {
  // Production routing is immutable: stale dashboard variables cannot re-enable a tunnel.
  if (process.env.VERCEL_ENV === "production") return PRODUCTION_BACKEND_URL;

  const raw = process.env.BACKEND_API_BASE_URL || process.env.NEXT_PUBLIC_API_BASE_URL || "";
  if (!raw || raw.startsWith("/")) return null;
  try {
    const parsed = new URL(raw);
    const isLocal = ["localhost", "127.0.0.1", "::1"].includes(parsed.hostname);
    if (parsed.protocol !== "https:" && !(isLocal && parsed.protocol === "http:")) return null;
    if (parsed.username || parsed.password) return null;
    if (process.env.VERCEL_ENV === "production" && parsed.hostname !== PRODUCTION_BACKEND_HOST) return null;
    parsed.pathname = parsed.pathname.replace(/\/+$/, "");
    parsed.search = "";
    parsed.hash = "";
    return parsed.toString().replace(/\/$/, "");
  } catch {
    return null;
  }
}

function requestTimeoutMs(): number {
  const raw = process.env.BACKEND_REQUEST_TIMEOUT_MS || "";
  const parsed = Number.parseInt(raw, 10);
  if (!Number.isFinite(parsed)) return DEFAULT_REQUEST_TIMEOUT_MS;
  return Math.min(MAX_REQUEST_TIMEOUT_MS, Math.max(MIN_REQUEST_TIMEOUT_MS, parsed));
}

function backendPath(path: string[]): string | null {
  if (!path.length) return null;
  const encoded = path.map((segment) => encodeURIComponent(segment)).join("/");
  const value = `/${encoded}`;
  const supportedApiRoot = value === "/api/v1" || value.startsWith("/api/v1/")
    || value === "/api/v2" || value.startsWith("/api/v2/");
  return supportedApiRoot ? value : null;
}

function isStateChanging(method: string): boolean {
  return ["POST", "PUT", "PATCH", "DELETE"].includes(method);
}

function isIdempotent(method: string): boolean {
  return method === "GET" || method === "HEAD";
}

function isRetryableStatus(status: number): boolean {
  return RETRYABLE_UPSTREAM_STATUSES.has(status);
}

function isTimeoutLike(error: unknown): boolean {
  return error instanceof Error && (error.name === "TimeoutError" || error.name === "AbortError");
}

function hasValidOrigin(request: NextRequest): boolean {
  const origin = request.headers.get("origin");
  return !origin || origin === request.nextUrl.origin;
}

function requestHeaders(request: NextRequest, path: string, baseUrl: string, id: string): Headers {
  const headers = new Headers();
  for (const name of FORWARDED_REQUEST_HEADERS) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  headers.set("x-request-id", id);

  // Vercel edge intercepts standard If-Match and returns 412 PRECONDITION_FAILED
  // before the request reaches the BFF Route Handler.  The browser sends the
  // custom X-SNAD-If-Match header which passes through Vercel untouched.  The
  // BFF translates it to the standard If-Match before forwarding to the backend.
  const customIfMatch = request.headers.get("x-snad-if-match");
  const standardIfMatch = request.headers.get("if-match");
  if (customIfMatch && standardIfMatch && customIfMatch !== standardIfMatch) {
    throw new Error("Conflicting If-Match and X-SNAD-If-Match values");
  }
  const ifMatchValue = customIfMatch || standardIfMatch;
  if (ifMatchValue) headers.set("if-match", ifMatchValue);

  if (path === REFRESH_PATH) {
    const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;
    if (refreshToken) headers.set(REFRESH_HEADER, refreshToken);
  }
  return headers;
}

function responseHeaders(upstream: Response, id: string, attempts: number): Headers {
  const headers = new Headers({
    "Cache-Control": "no-store",
    Pragma: "no-cache",
    Vary: "Cookie",
    "x-request-id": upstream.headers.get("x-request-id") || upstream.headers.get("x-correlation-id") || id,
    "x-sanad-bff-attempts": String(attempts),
  });
  for (const name of ["content-type", "x-correlation-id", "etag"]) {
    const value = upstream.headers.get(name);
    if (value) headers.set(name, value);
  }
  return headers;
}

function clearRefreshCookie(response: NextResponse): void {
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

function setSessionHint(response: NextResponse): void {
  response.cookies.set({
    name: SESSION_HINT_COOKIE,
    value: "1",
    httpOnly: false,
    secure: process.env.NODE_ENV === "production",
    sameSite: "strict",
    path: "/",
    maxAge: COOKIE_MAX_AGE_SECONDS,
  });
}

function clearSessionHint(response: NextResponse): void {
  response.cookies.set({
    name: SESSION_HINT_COOKIE,
    value: "",
    httpOnly: false,
    secure: process.env.NODE_ENV === "production",
    sameSite: "strict",
    path: "/",
    maxAge: 0,
  });
}

function applySessionCookiePolicy(response: NextResponse, upstream: Response, path: string): void {
  if (path === LOGOUT_PATH || (path === CHANGE_CREDENTIAL_PATH && upstream.ok)) {
    clearRefreshCookie(response);
    clearSessionHint(response);
    return;
  }

  if (path === REFRESH_PATH && (upstream.status === 401 || upstream.status === 403)) {
    clearRefreshCookie(response);
    clearSessionHint(response);
    return;
  }

  const refreshToken = upstream.headers.get(REFRESH_HEADER);
  if (refreshToken && path.startsWith(AUTH_PATH_PREFIX)) {
    response.cookies.set({
      name: REFRESH_COOKIE,
      value: refreshToken,
      httpOnly: true,
      secure: process.env.NODE_ENV === "production",
      sameSite: "strict",
      path: "/api/platform/api/v1/auth",
      maxAge: COOKIE_MAX_AGE_SECONDS,
    });
  }

  if ((path === LOGIN_PATH || path === REFRESH_PATH) && upstream.ok) {
    setSessionHint(response);
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function attemptTimeoutMs(deadline: number, attempt: number, totalAttempts: number): number {
  const remaining = deadline - Date.now();
  if (remaining <= 0) return 0;
  const attemptsLeft = totalAttempts - attempt + 1;
  const reservedRetryDelay = Math.max(0, attemptsLeft - 1) * RETRY_DELAY_MS;
  const sharedBudget = Math.floor((remaining - reservedRetryDelay) / attemptsLeft);
  return Math.min(remaining, Math.max(MIN_ATTEMPT_TIMEOUT_MS, sharedBudget));
}

async function fetchBackend(
  target: string,
  request: NextRequest,
  headers: Headers,
  body: ArrayBuffer | undefined,
): Promise<BackendResult> {
  const attempts = isIdempotent(request.method) ? MAX_IDEMPOTENT_ATTEMPTS : 1;
  const deadline = Date.now() + requestTimeoutMs();
  let lastFailure: BackendFailureKind = "network";

  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    const timeoutMs = attemptTimeoutMs(deadline, attempt, attempts);
    if (timeoutMs <= 0) throw new BackendRequestError("timeout", attempt - 1);
    try {
      const upstream = await fetch(target, {
        method: request.method,
        headers,
        body: body && body.byteLength > 0 ? body : undefined,
        cache: "no-store",
        redirect: "manual",
        signal: AbortSignal.timeout(timeoutMs),
      });
      if (!isRetryableStatus(upstream.status) || attempt === attempts) {
        return { response: upstream, attempts: attempt };
      }
      await upstream.body?.cancel().catch(() => undefined);
      lastFailure = "network";
    } catch (error) {
      lastFailure = isTimeoutLike(error) ? "timeout" : "network";
      if (attempt === attempts) throw new BackendRequestError(lastFailure, attempt);
    }
    if (deadline - Date.now() <= RETRY_DELAY_MS + MIN_ATTEMPT_TIMEOUT_MS) {
      throw new BackendRequestError(lastFailure, attempt);
    }
    await sleep(RETRY_DELAY_MS);
  }
  throw new BackendRequestError(lastFailure, attempts);
}

type RouteContext = { params: Promise<{ path: string[] }> };

async function proxy(request: NextRequest, context: RouteContext): Promise<NextResponse> {
  const id = requestId(request);
  if (isStateChanging(request.method) && !hasValidOrigin(request)) {
    return jsonError("Forbidden", 403, id);
  }

  const params = await context.params;
  const path = backendPath(params.path);
  if (!path) return jsonError("Not found", 404, id);

  const baseUrl = backendBaseUrl();
  if (!baseUrl) {
    console.error("Platform BFF backend URL is not configured or violates the production Render policy", { path, requestId: id });
    const response = jsonError("Service unavailable", 503, id);
    if (path === LOGOUT_PATH) {
      clearRefreshCookie(response);
      clearSessionHint(response);
    }
    return response;
  }

  const target = `${baseUrl}${path}${request.nextUrl.search}`;
  let headers: Headers;
  try {
    headers = requestHeaders(request, path, baseUrl, id);
  } catch (error) {
    if (error instanceof Error && error.message === "Conflicting If-Match and X-SNAD-If-Match values") {
      return jsonError(error.message, 400, id);
    }
    throw error;
  }

  try {
    const supportsBody = !["GET", "HEAD"].includes(request.method);
    const body = supportsBody ? await request.arrayBuffer() : undefined;
    const result = await fetchBackend(target, request, headers, body);
    const upstream = result.response;
    const response = new NextResponse(
      upstream.status === 204 || upstream.status === 304 ? null : upstream.body,
      { status: upstream.status, headers: responseHeaders(upstream, id, result.attempts) },
    );
    applySessionCookiePolicy(response, upstream, path);
    return response;
  } catch (error) {
    const failure = error instanceof BackendRequestError
      ? error
      : new BackendRequestError(isTimeoutLike(error) ? "timeout" : "network", 1);

    console.error("Platform BFF request failed", {
      errorName: failure.name,
      failureKind: failure.kind,
      attempts: failure.attempts,
      path,
      requestId: id,
    });

    const response = jsonError(
      failure.kind === "timeout" ? "Backend timeout" : "Backend unavailable",
      failure.kind === "timeout" ? 504 : 502,
      id,
      failure.kind,
    );
    if (path === LOGOUT_PATH) {
      clearRefreshCookie(response);
      clearSessionHint(response);
    }
    return response;
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
