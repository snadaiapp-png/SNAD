/** Executive Management route registry. */
export const executiveRoutes = {
  root: "/executive",
  dashboard: "/executive",
  tenants: "/executive",
  plans: "/executive",
  subscriptions: "/executive",
  billing: "/executive",
  organizations: (tenantId: string) => `/executive/tenants/${tenantId}/organizations`,
} as const;
