/**
 * Unified error model for the SANAD API client.
 *
 * Every error thrown by the API client is an instance of one of
 * these classes. Callers can use `instanceof` to branch on error
 * type programmatically.
 *
 * Design rules:
 * - Never expose raw stack traces to end users.
 * - Never log full HTML responses or sensitive headers.
 * - Preserve the original error cause where applicable.
 * - Each error class carries a stable `name` and `code` for
 *   programmatic branching.
 */

import type { ApiErrorDetails } from "./types";

export abstract class ApiClientError extends Error {
  abstract readonly code: string;
  override readonly cause?: unknown;

  constructor(message: string, cause?: unknown) {
    super(message);
    this.name = this.constructor.name;
    if (cause !== undefined) this.cause = cause;
    Object.setPrototypeOf(this, new.target.prototype);
  }

  toSafeSummary(): string {
    return `[${this.code}] ${this.message}`;
  }
}

export class ApiConfigurationError extends ApiClientError {
  readonly code = "API_CONFIGURATION_ERROR";
}

export class ApiTimeoutError extends ApiClientError {
  readonly code = "API_TIMEOUT_ERROR";
  readonly timeoutMs: number;

  constructor(message: string, timeoutMs: number, cause?: unknown) {
    super(message, cause);
    this.timeoutMs = timeoutMs;
  }
}

export class ApiNetworkError extends ApiClientError {
  readonly code = "API_NETWORK_ERROR";
}

export class ApiHttpError extends ApiClientError {
  readonly code = "API_HTTP_ERROR";
  readonly details: ApiErrorDetails;

  constructor(message: string, details: ApiErrorDetails, cause?: unknown) {
    super(message, cause);
    this.details = details;
  }

  get status(): number { return this.details.status; }
  get backendMessage(): string | null { return this.details.message; }
  get requestId(): string | null { return this.details.requestId; }

  override toSafeSummary(): string {
    const rid = this.details.requestId ? ` (requestId=${this.details.requestId})` : "";
    return `[${this.code}] HTTP ${this.details.status}: ${this.message}${rid}`;
  }
}

export class ApiRequestSerializationError extends ApiClientError {
  readonly code = "API_REQUEST_SERIALIZATION_ERROR";
}

export class ApiResponseParseError extends ApiClientError {
  readonly code = "API_RESPONSE_PARSE_ERROR";
  readonly statusCode: number | null;

  constructor(message: string, statusCode: number | null, cause?: unknown) {
    super(message, cause);
    this.statusCode = statusCode;
  }
}

export function isApiClientError(err: unknown): err is ApiClientError { return err instanceof ApiClientError; }
export function isApiTimeoutError(err: unknown): err is ApiTimeoutError { return err instanceof ApiTimeoutError; }
export function isApiNetworkError(err: unknown): err is ApiNetworkError { return err instanceof ApiNetworkError; }
export function isApiHttpError(err: unknown): err is ApiHttpError { return err instanceof ApiHttpError; }
export function isApiConfigurationError(err: unknown): err is ApiConfigurationError { return err instanceof ApiConfigurationError; }
export function isApiResponseParseError(err: unknown): err is ApiResponseParseError { return err instanceof ApiResponseParseError; }
export function isApiRequestSerializationError(err: unknown): err is ApiRequestSerializationError { return err instanceof ApiRequestSerializationError; }

export function isTimeoutAbortError(err: unknown, timeoutMs: number): err is DOMException {
  if (!(err instanceof DOMException) && !(err instanceof Error)) return false;
  const name = (err as { name?: string }).name;
  if (name !== "AbortError") return false;
  const msg = (err as Error).message?.toLowerCase() ?? "";
  if (msg.includes("timeout") || msg.includes("timed out")) return true;
  const reason = (err as { reason?: unknown }).reason;
  if (reason instanceof Error) {
    const reasonName = (reason as { name?: string }).name;
    if (reasonName === "TimeoutError") return true;
    const reasonMsg = reason.message?.toLowerCase() ?? "";
    if (reasonMsg.includes("timeout")) return true;
  }
  void timeoutMs;
  return false;
}

// Extended error types for unified API contract
import type { ApiProblem } from "./types";

/** Parse an error response into an ApiProblem */
export function parseApiProblem(response: unknown): ApiProblem | null {
  if (typeof response === "object" && response !== null && "code" in response) {
    return response as ApiProblem;
  }
  return null;
}
