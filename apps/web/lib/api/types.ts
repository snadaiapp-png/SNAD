/**
 * Shared type definitions for the SANAD API client.
 *
 * These types are environment-agnostic and can be used from both
 * Client Components and Server Components / Route Handlers.
 */

/**
 * HTTP methods supported by the API client.
 */
export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

/**
 * Query parameter value types supported by the URL builder.
 * - `undefined` values are silently skipped.
 * - `null` values are rendered as the literal string "null".
 * - Arrays are rendered as repeated key=value pairs.
 */
export type QueryParamValue = string | number | boolean | null | undefined;

/**
 * Record of query parameters for a single request.
 */
export type QueryParams = Record<string, QueryParamValue | QueryParamValue[]>;

/**
 * Request context — explicit per-call metadata that is NOT derived
 * from the URL or body.
 *
 * The backend currently uses `?tenantId=<uuid>` as a query parameter
 * (NOT a header — confirmed via Backend controller inspection).
 * The `tenantId` and `organizationId` fields here are convenience
 * accessors; callers must still pass them as query params on
 * tenant-scoped endpoints.
 *
 * The `headers` field allows callers to add custom headers. The
 * client enforces a protected-headers list to prevent accidental
 * override of security-critical headers.
 */
export interface ApiRequestContext {
  /** Optional tenant UUID. NOT sent as a header — pass as query param. */
  tenantId?: string;
  /** Optional organization UUID. NOT sent as a header — pass as query param. */
  organizationId?: string;
  /** Additional headers to merge with defaults. Cannot override protected headers. */
  headers?: Record<string, string>;
}

/**
 * Full request descriptor passed to `apiClient.request()`.
 */
export interface ApiRequest<TResponse, TBody = undefined> {
  method: HttpMethod;
  /** API path, with or without a leading `/`. Appended to the base URL. */
  path: string;
  /** Query parameters. `undefined` values are skipped. */
  query?: QueryParams;
  /** Request body. Serialized as JSON. `undefined` = no body. */
  body?: TBody;
  /** Per-request context (tenant, org, custom headers). */
  context?: ApiRequestContext;
  /** External AbortSignal for cancellation. Combined with the timeout signal. */
  signal?: AbortSignal;
  /** Per-request timeout in ms. Overrides the default. */
  timeoutMs?: number;
  /** Fetch cache mode (e.g. "no-store" for health checks). Optional. */
  cache?: RequestCache;
  /** Optional response type marker for the caller's reference. */
  _responseType?: TResponse;
}

/**
 * Normalized error details extracted from a Backend error response.
 *
 * The Backend's `ApiErrorResponse` shape is:
 *   { timestamp, status, error, message, path }
 *
 * For unhandled exceptions (401/403/422/5xx), Spring Boot's
 * `DefaultErrorAttributes` adds `requestId` and `exception`.
 * This interface accommodates both shapes defensively.
 */
export interface ApiErrorDetails {
  /** HTTP status code. */
  status: number;
  /** HTTP reason phrase (e.g. "Bad Request", "Not Found"). */
  error: string | null;
  /** Human-readable message from the Backend. Safe to display in dev tooling. */
  message: string | null;
  /** Request path as recorded by the Backend. */
  path: string | null;
  /** Request ID / correlation ID if the Backend provides one. */
  requestId: string | null;
  /** Raw error response body if JSON parsing succeeded, else null. */
  body: Record<string, unknown> | null;
}

/**
 * Result of a backend health check.
 *
 * `configured`, `reachable`, `statusCode`, `targetHost`, and `checkedAt`
 * are safe to expose via the public `/api/system/backend-status` route.
 * The `error` field is kept internal for logging but NEVER returned to the client.
 */
export interface HealthCheckResult {
  configured: boolean;
  reachable: boolean;
  statusCode: number | null;
  /** Backend hostname (no scheme, no path, no port unless non-standard) — safe to expose. */
  targetHost: string | null;
  /** UTC ISO-8601 timestamp when the check was performed. */
  checkedAt: string;
  /** Internal error message — must NOT be forwarded to end users. */
  error: string | null;
}

// ============================================================================
// SNAD Unified API Contract Types (Stage 03)
// ============================================================================

/** RFC 9457 Problem Details error response */
export type ApiProblem = {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  code: string;
  requestId: string;
  timestamp: string;
  errors?: FieldValidationError[];
};

/** Field-level validation error */
export type FieldValidationError = {
  field: string;
  code: string;
  message: string;
};

/** Unified paginated response */
export type PageResponse<T> = {
  content: T[];
  page: PageMetadata;
};

/** Page metadata */
export type PageMetadata = {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  hasNext: boolean;
  hasPrevious: boolean;
  sort: SortMetadata[];
};

/** Sort metadata */
export type SortMetadata = {
  field: string;
  direction: string;
};

/** Standard pagination request parameters */
export type PageRequestParams = {
  page?: number;
  size?: number;
  sort?: string;
};

/** Error codes matching backend ErrorCode enum */
export const ERROR_CODES = {
  GEN_001: "SANAD-GEN-001",
  VAL_001: "SANAD-VAL-001",
  AUTH_001: "SANAD-AUTH-001",
  AUTH_002: "SANAD-AUTH-002",
  SEC_001: "SANAD-SEC-001",
  TEN_001: "SANAD-TEN-001",
  RES_001: "SANAD-RES-001",
  CON_001: "SANAD-CON-001",
  RATE_001: "SANAD-RATE-001",
} as const;

/** Check if an error is an ApiProblem */
export function isApiProblem(error: unknown): error is ApiProblem {
  return (
    typeof error === "object" &&
    error !== null &&
    "code" in error &&
    "status" in error &&
    "requestId" in error
  );
}

/** Extract field errors from ApiProblem */
export function getFieldErrors(error: unknown): FieldValidationError[] {
  if (isApiProblem(error) && error.errors) {
    return error.errors;
  }
  return [];
}
