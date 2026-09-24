/**
 * Feature Flag Registry for Executive Management and System Health.
 * Each module has its own independent flag.
 */

export type FeatureFlag =
  | "EXECUTIVE_MODULE"
  | "SYSTEM_HEALTH_MODULE";

const DEFAULT_FLAGS: Record<FeatureFlag, boolean> = {
  EXECUTIVE_MODULE: true,
  SYSTEM_HEALTH_MODULE: true,
};

/**
 * Check if a feature flag is enabled.
 * Currently reads from defaults; in production this would read from
 * tenant configuration or environment variables.
 */
export function isFeatureEnabled(flag: FeatureFlag): boolean {
  return DEFAULT_FLAGS[flag] ?? false;
}

/**
 * Get all feature flags for the current context.
 */
export function getAllFeatureFlags(): Record<FeatureFlag, boolean> {
  return { ...DEFAULT_FLAGS };
}
