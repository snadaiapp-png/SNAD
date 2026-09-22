import { apiClient } from "./client";

// ── Types ────────────────────────────────────────────────────────────
export interface ExecutiveDashboard {
  totalTenants: number;
  activeTenants: number;
  trialTenants: number;
  totalRevenue: number;
  totalSubscriptions: number;
  totalInvoices: number;
  unpaidInvoices: number;
}

export interface ManagedTenant {
  id: string; name: string; legalName: string | null; subdomain: string;
  status: string; billingEmail: string | null; countryCode: string | null;
  locale: string; timezone: string; currencyCode: string;
  trialEndsAt: string | null; suspensionReason: string | null;
  createdAt: string; updatedAt: string;
}

export interface TenantProfileUpdate {
  name?: string;
  legalName?: string;
  billingEmail?: string;
  countryCode?: string;
  locale?: string;
  timezone?: string;
  currencyCode?: string;
}

export interface SaasPlan {
  id: string; code: string; name: string; description: string | null;
  status: string; currencyCode: string; monthlyPriceMinor: number;
  annualPriceMinor: number; trialDays: number; maxUsers: number;
  maxOrganizations: number; storageMb: number;
  entitlements: Entitlement[]; createdAt: string; updatedAt: string;
}

export interface TenantSubscription {
  id: string; tenantId: string; tenantName: string; planId: string;
  planCode: string; planName: string; pendingPlanId: string | null;
  pendingPlanCode: string | null; status: string;
  billingCycle: "MONTHLY" | "ANNUAL"; pendingBillingCycle: "MONTHLY" | "ANNUAL" | null;
  seatQuantity: number; creditBalanceMinor: number; currencyCode: string;
  startedAt: string; trialEndsAt: string | null;
  currentPeriodStart: string; currentPeriodEnd: string;
  cancelAtPeriodEnd: boolean; cancelledAt: string | null;
  createdAt: string; updatedAt: string;
}

export interface BillingInvoice {
  id: string; tenantId: string; tenantName: string; subscriptionId: string;
  invoiceNumber: string; status: string; currencyCode: string;
  subtotalMinor: number; creditAppliedMinor: number; taxMinor: number;
  totalMinor: number; amountPaidMinor: number; description: string | null;
  periodStart: string; periodEnd: string; dueAt: string;
  paidAt: string | null; paymentReference: string | null;
  createdAt: string; updatedAt: string;
}

export interface ManagedOrganization {
  id: string; tenantId: string; name: string; description: string | null;
  status: string; unitType: "GENERAL" | "LEGAL_ENTITY" | "BRANCH" | "DEPARTMENT" | "LOCATION";
  createdAt: string; updatedAt: string;
}

export interface ManagedMembership {
  id: string; tenantId: string; organizationId: string;
  userId: string | null; email: string; displayName: string | null;
  roleCode: string; status: string; createdAt: string; updatedAt: string;
}

export interface SubscriptionOperatingUnit {
  organizationId: string;
  organizationName: string;
  unitType: "GENERAL" | "LEGAL_ENTITY" | "BRANCH" | "DEPARTMENT" | "LOCATION";
  status: "ACTIVE" | "INACTIVE";
  billingMode: "CONSOLIDATED" | "SEPARATE";
}

export interface SubscriptionBillingProfile {
  id: string;
  organizationId: string | null;
  profileName: string;
  billingEmail: string | null;
  currencyCode: string;
  billingMode: "CONSOLIDATED" | "SEPARATE";
  status: "ACTIVE" | "INACTIVE";
}

export interface SubscriptionUnitApplication {
  applicationId: string;
  applicationCode: string;
  applicationName: string;
  enabled: boolean;
}

export interface SubscriptionResourceBinding {
  organizationId: string;
  resourceType: "WEBSITE" | "STORE" | "POS_LOCATION";
  resourceId: string;
  resourceName: string | null;
  status: "ACTIVE" | "INACTIVE";
}

export interface Entitlement {
  id?: string; featureCode: string; enabled: boolean; limitValue: number | null;
}

// ── Module Registry Types (EXECUTIVE V2) ───────────────────────────
export interface ModuleResponse {
  id: string; code: string; name: string; description: string | null;
  status: string; displayOrder: number; version: string | null;
  enabled: boolean; createdAt: string; updatedAt: string;
}

export interface PlanModuleEntitlementResponse {
  id: string; planId: string; moduleId: string; moduleCode: string;
  moduleEnabled: boolean; capabilityCode: string | null;
  capabilityValue: string | null; limitValue: number | null;
  quotaValue: number | null; quotaPeriod: string | null;
  effectiveAt: string; createdAt: string; updatedAt: string;
}

export interface QuotaResponse {
  value: number; period: string;
}

export interface TenantEntitlementResponse {
  tenantId: string; moduleCode: string; moduleEnabled: boolean;
  subscriptionId: string | null; planId: string | null;
  capabilities: Record<string, boolean>;
  limits: Record<string, number>;
  quotas: Record<string, QuotaResponse>;
  effectiveAt: string;
}

// ── API ──────────────────────────────────────────────────────────────
const root = "/api/v1/executive";

export const executiveApi = {
  dashboard: () => apiClient.get<ExecutiveDashboard>(`${root}/dashboard`),
  accessCheck: () => apiClient.get<{ authenticated: boolean; canRead: boolean; canWrite: boolean }>(`${root}/access-check`),
  tenants: () => apiClient.get<ManagedTenant[]>(`${root}/tenants`),
  tenant: (tenantId: string) => apiClient.get<ManagedTenant>(`${root}/tenants/${tenantId}`),
  createTenant: (body: { name: string; subdomain: string; adminEmail: string; adminDisplayName: string }) =>
    apiClient.post<ManagedTenant, typeof body>(`${root}/tenants`, body),
  updateTenant: (tenantId: string, body: TenantProfileUpdate) =>
    apiClient.patch<ManagedTenant, TenantProfileUpdate>(`${root}/tenants/${tenantId}`, body),
  changeTenantStatus: (tenantId: string, status: string, reason: string) =>
    apiClient.patch<ManagedTenant, { status: string; reason: string }>(`${root}/tenants/${tenantId}/status`, { status, reason }),
  recordTenantLoginLinkEvent: (tenantId: string, action: "OPEN" | "COPY") =>
    apiClient.post<void, { action: "OPEN" | "COPY" }>(
      `${root}/tenants/${tenantId}/login-link-events`,
      { action },
    ),
  plans: () => apiClient.get<SaasPlan[]>(`${root}/plans`),
  subscriptions: () => apiClient.get<TenantSubscription[]>(`${root}/subscriptions`),
  cancelSubscription: (subscriptionId: string, body: { immediate: boolean; reason: string }) =>
    apiClient.patch<TenantSubscription, typeof body>(`${root}/subscriptions/${subscriptionId}/cancel`, body),
  resumeSubscription: (subscriptionId: string) =>
    apiClient.patch<TenantSubscription, Record<string, never>>(`${root}/subscriptions/${subscriptionId}/resume`, {}),
  renewSubscription: (subscriptionId: string) =>
    apiClient.post<TenantSubscription, Record<string, never>>(`${root}/subscriptions/${subscriptionId}/renew`, {}),
  invoices: (tenantId: string) =>
    apiClient.get<BillingInvoice[]>(`${root}/billing/invoices?tenantId=${encodeURIComponent(tenantId)}`),
  organizations: (tenantId: string) => apiClient.get<ManagedOrganization[]>(`${root}/tenants/${tenantId}/organizations`),
  createOrganization: (
    tenantId: string,
    body: { name: string; description?: string | null; unitType?: ManagedOrganization["unitType"] },
  ) => apiClient.post<ManagedOrganization, typeof body>(
    `${root}/tenants/${tenantId}/organizations`,
    body,
  ),
  updateOrganization: (
    tenantId: string,
    organizationId: string,
    body: { name: string; description?: string | null; unitType?: ManagedOrganization["unitType"] },
  ) => apiClient.put<ManagedOrganization, typeof body>(
    `${root}/tenants/${tenantId}/organizations/${organizationId}`,
    body,
  ),
  memberships: (tenantId: string, organizationId: string) =>
    apiClient.get<ManagedMembership[]>(`${root}/tenants/${tenantId}/organizations/${organizationId}/memberships`),

  operatingUnits: (subscriptionId: string) =>
    apiClient.get<SubscriptionOperatingUnit[]>(`${root}/subscriptions/${subscriptionId}/operating-units`),
  operatingUnitApplications: (subscriptionId: string, organizationId: string) =>
    apiClient.get<SubscriptionUnitApplication[]>(
      `${root}/subscriptions/${subscriptionId}/operating-units/${organizationId}/applications`,
    ),
  subscriptionBillingProfiles: (subscriptionId: string) =>
    apiClient.get<SubscriptionBillingProfile[]>(
      `${root}/subscriptions/${subscriptionId}/billing-profiles`,
    ),
  subscriptionResourceBindings: (subscriptionId: string) =>
    apiClient.get<SubscriptionResourceBinding[]>(
      `${root}/subscriptions/${subscriptionId}/resource-bindings`,
    ),
  bindOperatingUnit: (
    subscriptionId: string,
    organizationId: string,
    billingMode: "CONSOLIDATED" | "SEPARATE",
  ) => apiClient.put<SubscriptionOperatingUnit, { billingMode: string }>(
    `${root}/subscriptions/${subscriptionId}/operating-units/${organizationId}`,
    { billingMode },
  ),
  deactivateOperatingUnit: (subscriptionId: string, organizationId: string) =>
    apiClient.delete<void>(`${root}/subscriptions/${subscriptionId}/operating-units/${organizationId}`),
  setOperatingUnitApplication: (
    subscriptionId: string,
    organizationId: string,
    applicationId: string,
    enabled: boolean,
  ) => apiClient.put<void, { enabled: boolean }>(
    `${root}/subscriptions/${subscriptionId}/operating-units/${organizationId}/applications/${applicationId}`,
    { enabled },
  ),
  unbindSubscriptionResource: (
    subscriptionId: string,
    resourceType: "WEBSITE" | "STORE" | "POS_LOCATION",
    resourceId: string,
  ) => apiClient.delete<void>(
    `${root}/subscriptions/${subscriptionId}/resources/${resourceType}/${resourceId}`,
  ),

  upsertSubscriptionBillingProfile: (
    subscriptionId: string,
    body: {
      organizationId?: string | null;
      profileName: string;
      billingEmail?: string | null;
      currencyCode: string;
      billingMode: "CONSOLIDATED" | "SEPARATE";
    },
  ) => apiClient.put<SubscriptionBillingProfile, typeof body>(
    `${root}/subscriptions/${subscriptionId}/billing-profile`,
    body,
  ),

  // Module Registry + Entitlements (EXECUTIVE V2)
  modules: () => apiClient.get<ModuleResponse[]>(`${root}/modules`),
  getModule: (moduleCode: string) => apiClient.get<ModuleResponse>(`${root}/modules/${moduleCode}`),
  planModules: (planId: string) => apiClient.get<PlanModuleEntitlementResponse[]>(`${root}/plans/${planId}/modules`),
  updatePlanModule: (planId: string, moduleCode: string, body: {
    moduleId: string; moduleEnabled: boolean; capabilityCode?: string | null;
    capabilityValue?: string | null; limitValue?: number | null;
    quotaValue?: number | null; quotaPeriod?: string | null;
  }) => apiClient.put<PlanModuleEntitlementResponse, typeof body>(`${root}/plans/${planId}/modules/${moduleCode}`, body),
  tenantEntitlements: (tenantId: string) => apiClient.get<TenantEntitlementResponse[]>(`${root}/tenants/${tenantId}/entitlements`),
  tenantModules: (tenantId: string) => apiClient.get<ModuleResponse[]>(`${root}/tenants/${tenantId}/modules`),
  recalculateEntitlements: (tenantId: string) =>
    apiClient.post<{ tenantId: string; status: string; timestamp: string }, Record<string, never>>(
      `${root}/tenants/${tenantId}/entitlements/recalculate`, {} as Record<string, never>),

  // Module Lifecycle (Mission 02)
  previewModuleReset: (tenantId: string, moduleCode: string) =>
    apiClient.get<ModuleResetPreview>(`${root}/tenants/${tenantId}/modules/${moduleCode}/reset/preview`),
  executeModuleReset: (tenantId: string, moduleCode: string) =>
    apiClient.post<ModuleResetResult, Record<string, never>>(
      `${root}/tenants/${tenantId}/modules/${moduleCode}/reset`, {} as Record<string, never>),
  previewSubscriptionImpact: (tenantId: string, targetPlanId: string) =>
    apiClient.get<SubscriptionImpactPreview>(`${root}/tenants/${tenantId}/subscription/impact/${targetPlanId}`),
};

// ── Mission 02 Types ─────────────────────────────────────────────────
export interface ModuleResetPreview {
  tenantId: string; moduleCode: string;
  affectedTables: { tableName: string; estimatedRows: number; classification: string }[];
  estimatedRows: number;
  protectedTables: string[];
  irreversible: boolean;
  previewGeneratedAt: string;
}

export interface ModuleResetResult {
  tenantId: string; moduleCode: string; status: string;
  tableResults: { tableName: string; rowsDeleted: number; success: boolean; errorMessage: string | null }[];
  totalRowsDeleted: number;
  startedAt: string; completedAt: string;
  errorMessage: string | null;
}

export interface SubscriptionImpactPreview {
  tenantId: string; currentPlanCode: string; targetPlanCode: string;
  changeType: string;
  moduleImpacts: {
    moduleCode: string; moduleName: string;
    currentlyEnabled: boolean; targetEnabled: boolean;
    status: string;
    capabilityChanges: { capabilityCode: string; capabilityType: string; currentValue: string; targetValue: string; changeType: string }[];
  }[];
  dataSafetyNote: string;
  previewGeneratedAt: string;
}
