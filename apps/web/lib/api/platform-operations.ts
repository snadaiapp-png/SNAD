import { apiClient } from "./client";

export interface AuditEntry { id: string; action: string; resourceType: string; resourceId: string | null; reason: string | null; result: "SUCCESS" | "FAILURE"; createdAt: string; }
export interface ExecutiveDashboard { totalTenants: number; activeTenants: number; trialTenants: number; suspendedTenants: number; totalUsers: number; activeUsers: number; operationalServices: number; degradedServices: number; recentActivity: AuditEntry[]; }
export interface ManagedTenant { id: string; name: string; legalName: string | null; subdomain: string; status: string; billingEmail: string | null; countryCode: string | null; locale: string; timezone: string; currencyCode: string; trialEndsAt: string | null; suspensionReason: string | null; createdAt: string; updatedAt: string; }
export interface SystemService { id: string; code: string; name: string; environment: string; status: string; ownerName: string | null; criticality: string; lastCheckedAt: string | null; lastLatencyMs: number | null; lastMessage: string | null; }

export interface Entitlement { id?: string; featureCode: string; enabled: boolean; limitValue: number | null; }
export interface SaasPlan { id: string; code: string; name: string; description: string | null; status: string; currencyCode: string; monthlyPriceMinor: number; annualPriceMinor: number; trialDays: number; maxUsers: number; maxOrganizations: number; storageMb: number; entitlements: Entitlement[]; createdAt: string; updatedAt: string; }
export interface TenantSubscription { id: string; tenantId: string; tenantName: string; planId: string; planCode: string; planName: string; pendingPlanId: string | null; pendingPlanCode: string | null; status: string; billingCycle: "MONTHLY" | "ANNUAL"; pendingBillingCycle: "MONTHLY" | "ANNUAL" | null; seatQuantity: number; creditBalanceMinor: number; currencyCode: string; startedAt: string; trialEndsAt: string | null; currentPeriodStart: string; currentPeriodEnd: string; cancelAtPeriodEnd: boolean; cancelledAt: string | null; createdAt: string; updatedAt: string; }
export interface BillingInvoice { id: string; tenantId: string; tenantName: string; subscriptionId: string; invoiceNumber: string; status: string; currencyCode: string; subtotalMinor: number; creditAppliedMinor: number; taxMinor: number; totalMinor: number; amountPaidMinor: number; description: string | null; periodStart: string; periodEnd: string; dueAt: string; paidAt: string | null; paymentReference: string | null; createdAt: string; updatedAt: string; }
export interface ManagedOrganization { id: string; tenantId: string; name: string; description: string | null; status: string; createdAt: string; updatedAt: string; }
export interface ManagedMembership { id: string; tenantId: string; organizationId: string; userId: string | null; email: string; displayName: string | null; roleCode: string; status: string; createdAt: string; updatedAt: string; }

export interface CreateTenantInput { name: string; legalName?: string; subdomain: string; billingEmail?: string; adminEmail: string; adminDisplayName: string; countryCode?: string; locale?: string; timezone?: string; currencyCode?: string; trialDays?: number; }
export interface PlanInput { code?: string; name: string; description?: string; currencyCode: string; monthlyPriceMinor: number; annualPriceMinor: number; trialDays: number; maxUsers: number; maxOrganizations: number; storageMb: number; entitlements: Array<Omit<Entitlement, "id">>; }
type StatusBody = { status: string; reason: string };
type SubscriptionCreateBody = { tenantId: string; planId: string; billingCycle: string; seatQuantity: number; trialDays?: number };
type ChangeSubscriptionPlanBody = { planId: string; billingCycle: string; effectiveMode: string; reason: string };
type ChangeSeatsBody = { seatQuantity: number; reason: string };
type CancelSubscriptionBody = { immediate: boolean; reason: string };
type MarkInvoicePaidBody = { paymentReference: string; reason: string };
type OrganizationBody = { name: string; description?: string };
type MembershipCreateBody = { email: string; displayName?: string; roleCode: string };
type MembershipUpdateBody = { status: string; roleCode: string; reason: string };

export const platformOperationsApi = {
  dashboard: () => apiClient.get<ExecutiveDashboard>("/api/v1/control-plane/dashboard"),
  tenants: () => apiClient.get<ManagedTenant[]>("/api/v1/control-plane/tenants"),
  createTenant: (body: CreateTenantInput) => apiClient.post<ManagedTenant, CreateTenantInput>("/api/v1/control-plane/tenants", body),
  changeTenantStatus: (tenantId: string, status: string, reason: string) => apiClient.patch<ManagedTenant, StatusBody>(`/api/v1/control-plane/tenants/${tenantId}/status`, { status, reason }),
  systems: () => apiClient.get<SystemService[]>("/api/v1/control-plane/systems"),
  audit: () => apiClient.get<AuditEntry[]>("/api/v1/control-plane/audit"),

  plans: () => apiClient.get<SaasPlan[]>("/api/v1/control-plane/plans"),
  createPlan: (body: PlanInput & { code: string }) => apiClient.post<SaasPlan, PlanInput & { code: string }>("/api/v1/control-plane/plans", body),
  updatePlan: (planId: string, body: PlanInput) => apiClient.put<SaasPlan, PlanInput>(`/api/v1/control-plane/plans/${planId}`, body),
  changePlanStatus: (planId: string, status: string, reason: string) => apiClient.patch<SaasPlan, StatusBody>(`/api/v1/control-plane/plans/${planId}/status`, { status, reason }),

  subscriptions: () => apiClient.get<TenantSubscription[]>("/api/v1/control-plane/subscriptions"),
  createSubscription: (body: SubscriptionCreateBody) => apiClient.post<TenantSubscription, SubscriptionCreateBody>("/api/v1/control-plane/subscriptions", body),
  changeSubscriptionPlan: (subscriptionId: string, body: ChangeSubscriptionPlanBody) => apiClient.patch<TenantSubscription, ChangeSubscriptionPlanBody>(`/api/v1/control-plane/subscriptions/${subscriptionId}/change-plan`, body),
  changeSubscriptionSeats: (subscriptionId: string, seatQuantity: number, reason: string) => apiClient.patch<TenantSubscription, ChangeSeatsBody>(`/api/v1/control-plane/subscriptions/${subscriptionId}/seats`, { seatQuantity, reason }),
  cancelSubscription: (subscriptionId: string, immediate: boolean, reason: string) => apiClient.patch<TenantSubscription, CancelSubscriptionBody>(`/api/v1/control-plane/subscriptions/${subscriptionId}/cancel`, { immediate, reason }),
  resumeSubscription: (subscriptionId: string) => apiClient.patch<TenantSubscription>(`/api/v1/control-plane/subscriptions/${subscriptionId}/resume`),
  renewSubscription: (subscriptionId: string) => apiClient.post<TenantSubscription>(`/api/v1/control-plane/subscriptions/${subscriptionId}/renew`),

  invoices: () => apiClient.get<BillingInvoice[]>("/api/v1/control-plane/billing/invoices"),
  markInvoicePaid: (invoiceId: string, paymentReference: string, reason: string) => apiClient.post<BillingInvoice, MarkInvoicePaidBody>(`/api/v1/control-plane/billing/invoices/${invoiceId}/mark-paid`, { paymentReference, reason }),

  organizations: (tenantId: string) => apiClient.get<ManagedOrganization[]>(`/api/v1/control-plane/tenants/${tenantId}/organizations`),
  createOrganization: (tenantId: string, body: OrganizationBody) => apiClient.post<ManagedOrganization, OrganizationBody>(`/api/v1/control-plane/tenants/${tenantId}/organizations`, body),
  updateOrganization: (tenantId: string, organizationId: string, body: OrganizationBody) => apiClient.put<ManagedOrganization, OrganizationBody>(`/api/v1/control-plane/tenants/${tenantId}/organizations/${organizationId}`, body),
  changeOrganizationStatus: (tenantId: string, organizationId: string, status: string, reason: string) => apiClient.patch<ManagedOrganization, StatusBody>(`/api/v1/control-plane/tenants/${tenantId}/organizations/${organizationId}/status`, { status, reason }),

  memberships: (tenantId: string, organizationId: string) => apiClient.get<ManagedMembership[]>(`/api/v1/control-plane/tenants/${tenantId}/organizations/${organizationId}/memberships`),
  createMembership: (tenantId: string, organizationId: string, body: MembershipCreateBody) => apiClient.post<ManagedMembership, MembershipCreateBody>(`/api/v1/control-plane/tenants/${tenantId}/organizations/${organizationId}/memberships`, body),
  updateMembership: (tenantId: string, organizationId: string, membershipId: string, body: MembershipUpdateBody) => apiClient.patch<ManagedMembership, MembershipUpdateBody>(`/api/v1/control-plane/tenants/${tenantId}/organizations/${organizationId}/memberships/${membershipId}`, body),
};