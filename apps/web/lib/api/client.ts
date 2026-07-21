/** Typed, reusable API client for the SANAD frontend. */
import { API_BASE_URL, IS_API_CONFIGURED, DEFAULT_API_TIMEOUT_MS, buildUrl, validateBaseUrl } from "./config";
import {
  ApiClientError,
  ApiConfigurationError,
  ApiHttpError,
  ApiNetworkError,
  ApiResponseParseError,
  ApiRequestSerializationError,
  ApiTimeoutError,
} from "./errors";
import type { ApiRequest, ApiRequestContext, ApiErrorDetails, QueryParams } from "./types";

const PROTECTED_HEADERS = new Set(["host", "content-length", "connection", "origin"]);
/** Headers that callers cannot set via context.headers (but the client can set via setDefaultHeader). */
const CALLER_PROTECTED_HEADERS = new Set(["host", "content-length", "connection", "origin", "authorization"]);

/**
 * Vercel intercepts the standard `If-Match` header at the edge/CDN level and
 * returns `412 PRECONDITION_FAILED` before the BFF Route Handler executes.  For
 * same-origin BFF requests (base URL starts with `/api/platform`) the client
 * rewrites `If-Match` to the custom `X-SNAD-If-Match` transport header that
 * passes through Vercel untouched.  The BFF translates it back before forwarding
 * to the backend.  For direct-backend URLs (local development) the standard
 * `If-Match` is preserved unchanged.
 */
const SAME_ORIGIN_BFF_PREFIX = "/api/platform";

function applyTransportHeaders(headers: Record<string, string>, baseUrl: string): void {
  const isSameOriginBff = baseUrl.startsWith(SAME_ORIGIN_BFF_PREFIX);
  if (isSameOriginBff && "If-Match" in headers) {
    headers["X-SNAD-If-Match"] = headers["If-Match"];
    delete headers["If-Match"];
  }
}

/**
 * Decide whether the request body should bypass JSON serialization and the
 * default `Content-Type: application/json` header. FormData and Blob bodies
 * must be handed to fetch verbatim so the browser can manage their framing
 * (e.g. multipart boundary for FormData).
 */
function isRawBody(body: unknown): boolean {
  if (body === undefined || body === null) return false;
  if (typeof FormData !== "undefined" && body instanceof FormData) return true;
  if (typeof Blob !== "undefined" && body instanceof Blob) return true;
  return false;
}

function mergeHeaders(hasBody: boolean, body: unknown, contextHeaders?: Record<string, string>, clientDefaultHeaders?: Record<string, string>): Record<string, string> {
  const merged: Record<string, string> = { Accept: "application/json" };
  // Only default to JSON Content-Type when the body is a JSON-serializable
  // payload. FormData/Blob bodies must NOT set Content-Type — the browser
  // sets the multipart boundary (or MIME type) itself.
  if (hasBody && !isRawBody(body)) merged["Content-Type"] = "application/json";
  // Apply client default headers (e.g. Authorization from auth provider) — these CAN set Authorization
  if (clientDefaultHeaders) {
    for (const [key, value] of Object.entries(clientDefaultHeaders)) {
      if (!PROTECTED_HEADERS.has(key.toLowerCase())) merged[key] = value;
    }
  }
  // Apply caller context headers — these CANNOT set Authorization (caller-protected)
  for (const [key, value] of Object.entries(contextHeaders ?? {})) {
    if (!CALLER_PROTECTED_HEADERS.has(key.toLowerCase())) merged[key] = value;
  }
  return merged;
}

function getHeader(response: Response, name: string): string | null {
  const headers = response.headers as Headers | undefined;
  return headers && typeof headers.get === "function" ? headers.get(name) : null;
}

async function extractErrorDetails(response: Response): Promise<ApiErrorDetails> {
  const status = response.status;
  const error = response.statusText || null;
  const headerRequestId = getHeader(response, "x-request-id") || getHeader(response, "request-id") || getHeader(response, "x-correlation-id") || null;
  let body: Record<string, unknown> | null = null;
  let message: string | null = null;
  let bodyPath: string | null = null;
  let bodyRequestId: string | null = null;
  try {
    if ((getHeader(response, "content-type") || "").includes("application/json") && typeof response.text === "function") {
      const text = await response.text();
      if (text) {
        body = JSON.parse(text) as Record<string, unknown>;
        if (typeof body.message === "string") message = body.message;
        else if (typeof body.error === "string") message = body.error;
        if (typeof body.path === "string") bodyPath = body.path;
        if (typeof body.requestId === "string") bodyRequestId = body.requestId;
      }
    }
  } catch { body = null; }
  return {
    status,
    error,
    message,
    path: bodyPath || response.url || null,
    requestId: headerRequestId || bodyRequestId,
    body,
  };
}

function createRequestSignal(timeoutMs: number, externalSignal?: AbortSignal): {
  signal: AbortSignal;
  cleanup: () => void;
  abortKind: () => "timeout" | "external" | null;
} {
  if (!Number.isFinite(timeoutMs) || timeoutMs <= 0) {
    throw new ApiConfigurationError(`Request timeout must be a positive finite number, got ${timeoutMs}`);
  }
  const controller = new AbortController();
  let kind: "timeout" | "external" | null = null;
  const abortFromExternal = () => {
    if (controller.signal.aborted) return;
    kind = "external";
    controller.abort(externalSignal?.reason ?? new DOMException("Request cancelled", "AbortError"));
  };
  if (externalSignal?.aborted) abortFromExternal();
  else externalSignal?.addEventListener("abort", abortFromExternal, { once: true });
  const timer = setTimeout(() => {
    if (controller.signal.aborted) return;
    kind = "timeout";
    controller.abort(new DOMException(`Request timed out after ${timeoutMs}ms`, "TimeoutError"));
  }, timeoutMs);
  return {
    signal: controller.signal,
    cleanup: () => {
      clearTimeout(timer);
      externalSignal?.removeEventListener("abort", abortFromExternal);
    },
    abortKind: () => kind,
  };
}

/** Path prefixes that should NOT trigger automatic token refresh on 401. */
const NO_REFRESH_PATHS = ["/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password"];

export class ApiClient {
  private readonly baseUrl: string;
  private readonly defaultTimeoutMs: number;
  private defaultHeaders: Record<string, string> = {};
  private unauthorizedHandler: (() => Promise<boolean>) | null = null;

  constructor(options?: { baseUrl?: string; timeoutMs?: number }) {
    this.baseUrl = options?.baseUrl ?? API_BASE_URL;
    this.defaultTimeoutMs = options?.timeoutMs ?? DEFAULT_API_TIMEOUT_MS;
  }

  /** Set a default header (e.g. Authorization) on all subsequent requests. */
  setDefaultHeader(name: string, value: string): void {
    this.defaultHeaders[name] = value;
  }

  /** Remove a default header. */
  removeDefaultHeader(name: string): void {
    delete this.defaultHeaders[name];
  }

  /**
   * Register a handler invoked when a request receives HTTP 401.
   * The handler should attempt to refresh the token and return true on success
   * (so the caller can retry) or false on failure (so the caller can propagate).
   * Set to null to disable auto-refresh.
   */
  setUnauthorizedHandler(handler: (() => Promise<boolean>) | null): void {
    this.unauthorizedHandler = handler;
  }

  buildUrl(path: string, query?: QueryParams): string {
    return buildUrl(this.baseUrl, path, query as Record<string, unknown>);
  }
  get isConfigured(): boolean { return this.baseUrl.length > 0; }
  /** The normalized base URL (no trailing slash). Safe to expose for health diagnostics. */
  get baseUrlValue(): string { return this.baseUrl; }

  async request<TResponse, TBody = undefined>(req: ApiRequest<TResponse, TBody>): Promise<TResponse> {
    validateBaseUrl(this.baseUrl);
    const hasBody = req.body !== undefined && req.body !== null;
    const timeoutMs = req.timeoutMs ?? this.defaultTimeoutMs;
    const fullUrl = this.buildUrl(req.path, req.query as QueryParams);
    const headers = mergeHeaders(hasBody, req.body, req.context?.headers, this.defaultHeaders);
    applyTransportHeaders(headers, this.baseUrl);

    let serializedBody: string | undefined;
    let rawBody: BodyInit | undefined;
    if (hasBody && req.method !== "GET") {
      if (typeof FormData !== "undefined" && req.body instanceof FormData) {
        // FormData must be passed through verbatim so the browser can set the
        // multipart boundary. JSON.stringify would produce "{}" and break uploads.
        rawBody = req.body as unknown as BodyInit;
      } else if (typeof Blob !== "undefined" && req.body instanceof Blob) {
        rawBody = req.body as unknown as BodyInit;
      } else {
        try { serializedBody = JSON.stringify(req.body); }
        catch (err) { throw new ApiRequestSerializationError(`Failed to serialize request body for ${req.method} ${req.path}`, err); }
        if (serializedBody === undefined) throw new ApiRequestSerializationError(`Request body for ${req.method} ${req.path} is not JSON-serializable`);
      }
    }

    const requestSignal = createRequestSignal(timeoutMs, req.signal);
    try {
      if (requestSignal.abortKind() === "external") {
        throw new ApiClientCancellation(`Request to ${req.method} ${req.path} was cancelled`, req.signal?.reason);
      }
      const init: RequestInit = { method: req.method, headers, credentials: "include", signal: requestSignal.signal };
      if (req.cache !== undefined) init.cache = req.cache;
      if (serializedBody !== undefined) init.body = serializedBody;
      else if (rawBody !== undefined) init.body = rawBody;
      let response = await fetch(fullUrl, init);

      // Auto-refresh on 401: if the token expired, attempt to refresh and retry once.
      if (response.status === 401
          && this.unauthorizedHandler
          && !NO_REFRESH_PATHS.some((p) => req.path.startsWith(p))) {
        const refreshed = await this.unauthorizedHandler();
        if (refreshed) {
          // Rebuild headers with the new Authorization token and retry.
          const retryHeaders = mergeHeaders(hasBody, req.body, req.context?.headers, this.defaultHeaders);
          applyTransportHeaders(retryHeaders, this.baseUrl);
          const retryInit: RequestInit = { method: req.method, headers: retryHeaders, credentials: "include", signal: requestSignal.signal };
          if (req.cache !== undefined) retryInit.cache = req.cache;
          if (serializedBody !== undefined) retryInit.body = serializedBody;
          else if (rawBody !== undefined) retryInit.body = rawBody;
          response = await fetch(fullUrl, retryInit);
        }
      }

      if (response.status === 204) return undefined as TResponse;
      if (!response.ok) {
        const details = await extractErrorDetails(response);
        throw new ApiHttpError(`HTTP ${details.status} ${details.error || ""}: ${req.method} ${req.path}`.trim(), details);
      }
      if (getHeader(response, "content-length") === "0") return undefined as TResponse;
      if (!(getHeader(response, "content-type") || "").includes("application/json")) return undefined as TResponse;
      const text = typeof response.text === "function" ? await response.text() : "";
      if (!text) return undefined as TResponse;
      try { return JSON.parse(text) as TResponse; }
      catch (err) { throw new ApiResponseParseError(`Failed to parse JSON response from ${req.method} ${req.path}`, response.status, err); }
    } catch (err) {
      if (err instanceof ApiClientError) throw err;
      const kind = requestSignal.abortKind();
      if (kind === "timeout") throw new ApiTimeoutError(`Request to ${req.method} ${req.path} timed out after ${timeoutMs}ms`, timeoutMs, err);
      if (kind === "external" || isAbortLike(err)) {
        throw new ApiClientCancellation(`Request to ${req.method} ${req.path} was cancelled: ${safeErrorMessage(err)}`, err);
      }
      throw new ApiNetworkError(`Network error while requesting ${req.method} ${req.path}: ${safeErrorMessage(err)}`, err);
    } finally { requestSignal.cleanup(); }
  }

  get<TResponse>(path: string, options?: RequestOptions): Promise<TResponse> { return this.request<TResponse>({ method: "GET", path, ...options }); }
  post<TResponse, TBody = undefined>(path: string, body?: TBody, options?: RequestOptions): Promise<TResponse> { return this.request<TResponse, TBody>({ method: "POST", path, body, ...options }); }
  put<TResponse, TBody = undefined>(path: string, body?: TBody, options?: RequestOptions): Promise<TResponse> { return this.request<TResponse, TBody>({ method: "PUT", path, body, ...options }); }
  patch<TResponse, TBody = undefined>(path: string, body?: TBody, options?: RequestOptions): Promise<TResponse> { return this.request<TResponse, TBody>({ method: "PATCH", path, body, ...options }); }
  delete<TResponse>(path: string, options?: RequestOptions): Promise<TResponse> { return this.request<TResponse>({ method: "DELETE", path, ...options }); }

  /**
   * Issue an authenticated GET and return the response body as a Blob.
   *
   * Used for endpoints that return non-JSON payloads (e.g. CSV downloads)
   * where the JSON-deserializing request() method would discard the body.
   * The same timeout, auth-header, and 401-refresh behavior applies.
   */
  async getBlob(path: string, options?: RequestOptions): Promise<Blob> {
    validateBaseUrl(this.baseUrl);
    const timeoutMs = options?.timeoutMs ?? this.defaultTimeoutMs;
    const fullUrl = this.buildUrl(path, options?.query as QueryParams);
    const headers = mergeHeaders(false, undefined, options?.context?.headers, this.defaultHeaders);
    applyTransportHeaders(headers, this.baseUrl);
    const requestSignal = createRequestSignal(timeoutMs, options?.signal);
    try {
      const init: RequestInit = { method: "GET", headers, credentials: "include", signal: requestSignal.signal };
      if (options?.cache !== undefined) init.cache = options.cache;
      let response = await fetch(fullUrl, init);
      if (response.status === 401
          && this.unauthorizedHandler
          && !NO_REFRESH_PATHS.some((p) => path.startsWith(p))) {
        const refreshed = await this.unauthorizedHandler();
        if (refreshed) {
          const retryHeaders = mergeHeaders(false, undefined, options?.context?.headers, this.defaultHeaders);
          applyTransportHeaders(retryHeaders, this.baseUrl);
          const retryInit: RequestInit = { method: "GET", headers: retryHeaders, credentials: "include", signal: requestSignal.signal };
          if (options?.cache !== undefined) retryInit.cache = options.cache;
          response = await fetch(fullUrl, retryInit);
        }
      }
      if (!response.ok) {
        const details = await extractErrorDetails(response);
        throw new ApiHttpError(`HTTP ${details.status} ${details.error || ""}: GET ${path}`.trim(), details);
      }
      return await response.blob();
    } catch (err) {
      if (err instanceof ApiClientError) throw err;
      const kind = requestSignal.abortKind();
      if (kind === "timeout") throw new ApiTimeoutError(`Request to GET ${path} timed out after ${timeoutMs}ms`, timeoutMs, err);
      if (kind === "external" || isAbortLike(err)) {
        throw new ApiClientCancellation(`Request to GET ${path} was cancelled: ${safeErrorMessage(err)}`, err);
      }
      throw new ApiNetworkError(`Network error while requesting GET ${path}: ${safeErrorMessage(err)}`, err);
    } finally { requestSignal.cleanup(); }
  }
}

type RequestOptions = { query?: QueryParams; context?: ApiRequestContext; signal?: AbortSignal; timeoutMs?: number; cache?: RequestCache };

export class ApiClientCancellation extends ApiClientError {
  readonly code = "API_CLIENT_CANCELLATION";
  constructor(message = "Request cancelled", cause?: unknown) { super(message, cause); }
}

function isAbortLike(err: unknown): boolean {
  return err instanceof DOMException && (err.name === "AbortError" || err.name === "TimeoutError");
}
function safeErrorMessage(err: unknown): string { return err instanceof Error ? err.message : String(err); }

export const apiClient = new ApiClient();
export { API_BASE_URL, IS_API_CONFIGURED, DEFAULT_API_TIMEOUT_MS };
