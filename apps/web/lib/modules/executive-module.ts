/** Executive Management module registry entry. */
export const executiveModule = {
  id: "executive",
  name: "Executive Management",
  description: "Tenant management, directory, plans, subscriptions, and billing",
  icon: "ExecutiveIcon",
  route: "/executive",
  featureFlag: "EXECUTIVE_MODULE" as const,
  capabilities: ["EXECUTIVE_VIEW", "EXECUTIVE_MANAGE", "EXECUTIVE_BILLING"],
  navigation: "executive-navigation",
  routes: "executive-routes",
} as const;
