/**
 * @deprecated Use system-health-api.ts instead.
 * This file re-exports from the new module for backward compatibility.
 */
export {
  type PlatformHealth,
  type ServiceHealth,
  type TenantHealth,
  type RiskForecastPoint,
  type HealthActionInput,
  type HealthActionResult,
  systemHealthApi as healthIntelligenceApi,
} from "./system-health-api";
