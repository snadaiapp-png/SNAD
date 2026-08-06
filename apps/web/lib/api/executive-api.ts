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
  status: string; createdAt: string; updatedAt: string;
}

export interface ManagedMembership {
  id: string; tenantId: string; organizationId: string;
  userId: string | null; email: string; displayName: string | null;
  roleCode: string; status: string; createdAt: string; updatedAt: string;
}

export interface Entitlement {
  id?: string; featureCode: string; enabled: boolean; limitValue: number | null;
}

// ── API ──────────────────────────────────────────────────────────────
const root = "/api/v1/executive";

export const executiveApi = {
  dashboard: () => apiClient.get<ExecutiveDashboard>(`${root}/dashboard`),
  accessCheck: () => apiClient.get<{ authenticated: boolean; canRead: boolean; canWrite: boolean }>(`${root}/access-check`),
  tenants: () => apiClient.get<ManagedTenant[]>(`${root}/tenants`),
  createTenant: (body: { name: string; subdomain: string; adminEmail: string; adminDisplayName: string }) =>
    apiClient.post<ManagedTenant, typeof body>(`${root}/tenants`, body),
  changeTenantStatus: (tenantId: string, status: string, reason: string) =>
    apiClient.patch<ManagedTenant, { status: string; reason: string }>(`${root}/tenants/${tenantId}/status`, { status, reason }),
  plans: () => apiClient.get<SaasPlan[]>(`${root}/plans`),
  subscriptions: () => apiClient.get<TenantSubscription[]>(`${root}/subscriptions`),
  invoices: () => apiClient.get<BillingInvoice[]>(`${root}/billing/invoices`),
  organizations: (tenantId: string) => apiClient.get<ManagedOrganization[]>(`${root}/tenants/${tenantId}/organizations`),
  memberships: (tenantId: string, organizationId: string) =>
    apiClient.get<ManagedMembership[]>(`${root}/tenants/${tenantId}/organizations/${organizationId}/memberships`),
};
