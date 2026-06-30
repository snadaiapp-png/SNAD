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

function mergeHeaders(hasBody: boolean, contextHeaders?: Record<string, string>, clientDefaultHeaders?: Record<string, string>): Record<string, string> {
  const merged: Record<string, string> = { Accept: "application/json" };
  if (hasBody) merged["Content-Type"] = "application/json";
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
  let bodyCode: string | null = null;
  try {
    const contentType = (getHeader(response, "content-type") || "").toLowerCase();
    // Stage 03A: accept both application/json and application/problem+json
    if ((contentType.includes("application/json") || contentType.includes("application/problem+json"))
        && typeof response.text === "function") {
      const text = await response.text();
      if (text) {
        body = JSON.parse(text) as Record<string, unknown>;
        // Unified ApiProblem shape (Stage 03A): { code, title, detail, instance, requestId, timestamp, errors }
        if (typeof body.code === "string") bodyCode = body.code;
        if (typeof body.detail === "string") message = body.detail;
        else if (typeof body.title === "string") message = body.title;
        else if (typeof body.message === "string") message = body.message;
        else if (typeof body.error === "string") message = body.error;
        if (typeof body.instance === "string") bodyPath = body.instance;
        else if (typeof body.path === "string") bodyPath = body.path;
        if (typeof body.requestId === "string") bodyRequestId = body.requestId;
      }
    }
  } catch { body = null; }
  return {
    status,
    error: bodyCode ?? error,
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

export class ApiClient {
  private readonly baseUrl: string;
  private readonly defaultTimeoutMs: number;
  private defaultHeaders: Record<string, string> = {};

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
    const headers = mergeHeaders(hasBody, req.context?.headers, this.defaultHeaders);

    let serializedBody: string | undefined;
    if (hasBody && req.method !== "GET") {
      try { serializedBody = JSON.stringify(req.body); }
      catch (err) { throw new ApiRequestSerializationError(`Failed to serialize request body for ${req.method} ${req.path}`, err); }
      if (serializedBody === undefined) throw new ApiRequestSerializationError(`Request body for ${req.method} ${req.path} is not JSON-serializable`);
    }

    const requestSignal = createRequestSignal(timeoutMs, req.signal);
    try {
      if (requestSignal.abortKind() === "external") {
        throw new ApiClientCancellation(`Request to ${req.method} ${req.path} was cancelled`, req.signal?.reason);
      }
      const init: RequestInit = { method: req.method, headers, credentials: "include", signal: requestSignal.signal };
      if (req.cache !== undefined) init.cache = req.cache;
      if (serializedBody !== undefined) init.body = serializedBody;
      const response = await fetch(fullUrl, init);
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
   * Stage 03A — Paginated GET helper.
   *
   * Returns a PageResponse<T> with { content: T[], page: PageMetadata }.
   * The `sort` parameter can be a single string ("name,asc") or an array
   * of such strings (["name,asc", "createdAt,desc"]) which is serialized
   * as repeated query params (?sort=name,asc&sort=createdAt,desc).
   */
  getPage<T>(path: string, options?: { query?: QueryParams; context?: ApiRequestContext; signal?: AbortSignal; timeoutMs?: number; cache?: RequestCache; sort?: string | string[] }): Promise<import("./types").PageResponse<T>> {
    const opts = options ?? {};
    const mergedQuery: QueryParams = { ...(opts.query ?? {}) };
    if (opts.sort !== undefined) {
      mergedQuery.sort = Array.isArray(opts.sort) ? opts.sort : [opts.sort];
    }
    return this.request<import("./types").PageResponse<T>>({
      method: "GET",
      path,
      query: mergedQuery,
      context: opts.context,
      signal: opts.signal,
      timeoutMs: opts.timeoutMs,
      cache: opts.cache,
    });
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
