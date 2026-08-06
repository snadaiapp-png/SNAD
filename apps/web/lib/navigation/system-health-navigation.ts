/** System Health navigation registry. */
export interface NavItem {
  id: string;
  labelKey: string;
  href: string;
  capability: string;
}

export const systemHealthNavigation: NavItem[] = [
  { id: "overview", labelKey: "nav.systemHealth.overview", href: "/system-health", capability: "SYSTEM_HEALTH_VIEW" },
  { id: "systems", labelKey: "nav.systemHealth.systems", href: "/system-health", capability: "SYSTEM_HEALTH_VIEW" },
  { id: "alerts", labelKey: "nav.systemHealth.alerts", href: "/system-health", capability: "SYSTEM_HEALTH_ALERTS" },
];
