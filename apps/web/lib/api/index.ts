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

// Validation (EXEC-PROMPT-031)
export {
  isValidUuid,
  requireValidUuid,
  isValidEmail,
  requireValidEmail,
  normalizeEmail,
  MAX_EMAIL_LENGTH,
  normalizeDisplayName,
  requireValidDisplayName,
  MAX_DISPLAY_NAME_LENGTH,
} from "./validation";

// Users API (EXEC-PROMPT-031)
export { usersApi, createUsersApi } from "./users";
export type {
  UserStatus,
  UserResponse,
  CreateUserRequest,
  UpdateUserRequest,
  UserLifecycleAction,
} from "./users";

// Memberships API (EXEC-PROMPT-031)
export { membershipsApi, createMembershipsApi } from "./memberships";
export type {
  MembershipStatus,
  OrganizationMembershipResponse,
  InviteOrganizationMemberRequest,
  MembershipLifecycleAction,
} from "./memberships";

// User-facing error mapper (EXEC-PROMPT-031)
export { toUserFacingError, toUserFacingMessage, toUserFacingTitle } from "./user-facing-errors";
export type { UserFacingError } from "./user-facing-errors";
