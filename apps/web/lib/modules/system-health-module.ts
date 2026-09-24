/** System Health module registry entry. */
export const systemHealthModule = {
  id: "system-health",
  name: "System Health",
  description: "Infrastructure monitoring, diagnostics, and system status",
  icon: "SystemHealthIcon",
  route: "/system-health",
  featureFlag: "SYSTEM_HEALTH_MODULE" as const,
  capabilities: ["SYSTEM_HEALTH_VIEW", "SYSTEM_HEALTH_MONITOR", "SYSTEM_HEALTH_ALERTS"],
  navigation: "system-health-navigation",
  routes: "system-health-routes",
} as const;
