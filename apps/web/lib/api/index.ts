/** Public API for the SANAD frontend API client. */
export {
  API_BASE_URL,
  IS_API_CONFIGURED,
  DEFAULT_API_TIMEOUT_MS,
  buildUrl,
  buildSearchParams,
  validateBaseUrl,
  buildApiUrl,
  API_FETCH_OPTIONS,
  API_TIMEOUT_MS,
} from "./config";
export { ApiClient, apiClient, ApiClientCancellation } from "./client";
export {
  ApiClientError,
  ApiConfigurationError,
  ApiTimeoutError,
  ApiNetworkError,
  ApiHttpError,
  ApiResponseParseError,
  ApiRequestSerializationError,
  isApiClientError,
  isApiTimeoutError,
  isApiNetworkError,
  isApiHttpError,
  isApiConfigurationError,
  isApiResponseParseError,
  isApiRequestSerializationError,
} from "./errors";
export { checkBackendIntegration } from "./health";
export type {
  HttpMethod,
  QueryParamValue,
  QueryParams,
  ApiRequestContext,
  ApiRequest,
  ApiErrorDetails,
  HealthCheckResult,
} from "./types";
