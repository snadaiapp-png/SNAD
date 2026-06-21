/**
 * @deprecated This module is preserved for backward compatibility.
 * Import from `@/lib/api` instead.
 */
import type { HealthCheckResult } from "./api/types";
export { checkBackendIntegration } from "./api";
export { API_BASE_URL, IS_API_CONFIGURED } from "./api";
export type IntegrationCheckResult = HealthCheckResult;
