import { apiClient } from "./client";

/**
 * SNAD Subscription Control Plane — typed API surface.
 *
 * These are the additive `/api/v1/executive` read models and commands
 * introduced by the control plane (overview, tenants/v2, subscriptions/v2,
 * detail, catalog, versions, prices, items, usage, provisioning, audit/v2).
 * Legacy endpoints remain in `executive-api.ts`.
 */

const root = "/api/v1/executive";

// ── Shared pagination contract ───────────────────────────────────────
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PageQuery {
  page?: number;
  size?: number;
  sort?: string;
  direction?: "ASC" | "DESC";
}

// ── Overview ─────────────────────────────────────────────────────────
export interface ScpOverview {
  totalTenants: number;
  activeSubscriptions: number;
  trials: number;
  pastDue: number;
  renewalsNext30Days: number;
  /** MRR in minor units, keyed by currency — never merged across currencies. */
  mrrMinorByCurrency: Record<string, number>;
  arrMinorByCurrency: Record<string, number>;
  /** null = not computable from current data; the UI must render N/A. */
  churnPercent: number | null;
  expansionRevenueMinor: number | null;
  generatedAt: string;
}

// ── Tenants ──────────────────────────────────────────────────────────
export interface TenantRow {
  id: string;
  name: string;
  code: string;
  status: string;
  countryCode: string | null;
  currencyCode: string | null;
  subscriptionCount: number;
  subscriptionStatus: string | null;
  createdAt: string;
}

export interface TenantQuery extends PageQuery {
  search?: string;
  status?: string;
  country?: string;
}

// ── Applications catalog ─────────────────────────────────────────────
export interface ScpApplication {
  id: string;
  code: string;
  name: string;
  localizedName: string | null;
  description: string | null;
  category: string;
  status: string;
  version: string | null;
  displayOrder: number;
  iconKey: string | null;
  provisioningMode: string;
  supportedCountries: string[] | null;
  dependencies: string[] | null;
  createdAt: string;
  updatedAt: string;
}

export interface ApplicationInput {
  code: string;
  name: string;
  localizedName?: string;
  description?: string;
  category?: string;
  iconKey?: string;
  provisioningMode?: string;
  supportedCountries?: string[];
  dependencies?: string[];
  displayOrder?: number;
}

// ── Plans & versions & prices ────────────────────────────────────────
export interface PlanVersion {
  id: string;
  planId: string;
  versionNumber: number;
  status: "DRAFT" | "ACTIVE" | "RETIRED";
  effectiveFrom: string | null;
  effectiveTo: string | null;
  currencyCode: string;
  monthlyPriceMinor: number;
  annualPriceMinor: number;
  trialDays: number;
  maxUsers: number;
  maxOrganizations: number;
  storageMb: number;
  createdAt: string;
  updatedAt: string;
}

export interface PlanVersionInput {
  currencyCode: string;
  monthlyPriceMinor: number;
  annualPriceMinor: number;
  trialDays: number;
  maxUsers: number;
  maxOrganizations: number;
  storageMb: number;
}

export interface Price {
  id: string;
  planVersionId: string | null;
  productId: string | null;
  priceModel: string;
  countryCode: string;
  currencyCode: string;
  billingInterval: string;
  baseAmountMinor: number;
  unitAmountMinor: number | null;
  tiersJson: string | null;
  minAmountMinor: number | null;
  maxAmountMinor: number | null;
  effectiveFrom: string | null;
  effectiveTo: string | null;
}

export interface PriceInput {
  priceModel: string;
  countryCode?: string;
  currencyCode: string;
  billingInterval?: string;
  baseAmountMinor?: number;
  unitAmountMinor?: number | null;
  tiersJson?: string | null;
  minAmountMinor?: number | null;
  maxAmountMinor?: number | null;
}

export interface CountryCurrency {
  countryCode: string;
  currencyCode: string;
  isDefault: boolean;
}

// ── Subscriptions ────────────────────────────────────────────────────
export interface SubscriptionRow {
  id: string;
  tenantId: string;
  tenantName: string;
  tenantCountry: string | null;
  status: string;
  billingCycle: string;
  seatQuantity: number;
  planId: string | null;
  planName: string | null;
  planCode: string | null;
  planVersion: string | null;
  currencyCode: string | null;
  monthlyPriceMinor: number | null;
  itemCount: number;
  trial: boolean;
  cancelAtPeriodEnd: boolean;
}

export interface SubscriptionQuery extends PageQuery {
  tenantId?: string;
  status?: string;
  country?: string;
  search?: string;
  trialOnly?: boolean;
}

export interface SubscriptionItem {
  id: string;
  tenantId: string;
  subscriptionId: string;
  itemType: "PLAN" | "ADD_ON" | "METERED" | "OTHER";
  applicationId: string | null;
  productId: string | null;
  planId: string | null;
  planVersionId: string | null;
  nameSnapshot: string | null;
  quantity: number;
  unitAmountMinor: number | null;
  currencyCode: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface SubscriptionDetail {
  id: string;
  overview: Record<string, unknown>;
  items: Array<Record<string, unknown>>;
  invoices: Array<Record<string, unknown>>;
  changes: Array<Record<string, unknown>>;
  provisioningJobs: Array<Record<string, unknown>>;
  audit: Array<Record<string, unknown>>;
}

export interface ChangePreview {
  subscriptionId: string;
  targetPlanVersionId: string;
  fromStatus: string;
  currentItems: Array<{
    itemId: string;
    itemType: string;
    name: string | null;
    quantity: number;
    unitAmountMinor: number | null;
    currencyCode: string | null;
  }>;
  currentMonthlyMinor: number | null;
  targetMonthlyMinor: number | null;
  deltaMonthlyMinor: number | null;
  currencyCode: string | null;
  warnings: string[];
}

export type LifecycleCommand =
  | "ACTIVATE"
  | "START_TRIAL"
  | "PAUSE"
  | "RESUME"
  | "SUSPEND"
  | "CANCEL"
  | "RENEW"
  | "EXPIRE"
  | "TERMINATE"
  | "SCHEDULE_CANCELLATION";

export interface CommandResult {
  subscriptionId: string;
  command: string;
  fromStatus: string;
  toStatus: string;
}

// ── Usage / provisioning / audit ─────────────────────────────────────
export interface UsageSnapshot {
  metricCode: string;
  current: number;
  limit: number | null;
  percent: number | null;
  limitKind: string;
  warning: boolean;
}

export interface ProvisioningJob {
  id: string;
  tenantId: string;
  subscriptionId: string;
  action: string;
  status: string;
  attempts: number;
  startedAt: string | null;
  completedAt: string | null;
  errorCode: string | null;
  createdAt: string;
}

export interface AuditEntry {
  id: string;
  action: string;
  resourceType: string;
  resourceId: string;
  reason: string | null;
  result: string;
  createdAt: string;
}

export interface AccessCheckV2 {
  authenticated: boolean;
  capabilities: Record<string, boolean>;
}

// ── Query serialization ──────────────────────────────────────────────
function qs(params: object): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

// ── API surface ──────────────────────────────────────────────────────
export const scpApi = {
  overview: () => apiClient.get<ScpOverview>(`${root}/overview`),

  tenants: (query: TenantQuery = {}) =>
    apiClient.get<PageResponse<TenantRow>>(`${root}/tenants/v2${qs(query)}`),

  applications: (availableOnly = false) =>
    apiClient.get<ScpApplication[]>(`${root}/applications${qs({ availableOnly })}`),

  createApplication: (body: ApplicationInput) =>
    apiClient.post<ScpApplication, ApplicationInput>(`${root}/applications`, body),

  planVersions: (planId: string) =>
    apiClient.get<PlanVersion[]>(`${root}/plans/${planId}/versions`),

  createPlanVersion: (planId: string, body: PlanVersionInput) =>
    apiClient.post<PlanVersion, PlanVersionInput>(`${root}/plans/${planId}/versions`, body),

  activatePlanVersion: (planId: string, versionId: string) =>
    apiClient.post<PlanVersion, Record<string, never>>(
      `${root}/plans/${planId}/versions/${versionId}/activate`,
      {},
    ),

  planVersionPrices: (planId: string, versionId: string) =>
    apiClient.get<Price[]>(`${root}/plans/${planId}/versions/${versionId}/prices`),

  productPrices: (productId: string) =>
    apiClient.get<Price[]>(`${root}/products/${productId}/prices`),

  countryCurrencies: () => apiClient.get<CountryCurrency[]>(`${root}/country-currencies`),

  subscriptionItems: (subscriptionId: string, activeOnly = false) =>
    apiClient.get<SubscriptionItem[]>(
      `${root}/subscriptions/${subscriptionId}/items${qs({ activeOnly })}`,
    ),

  subscriptions: (query: SubscriptionQuery = {}) =>
    apiClient.get<PageResponse<SubscriptionRow>>(`${root}/subscriptions/v2${qs(query)}`),

  subscriptionDetail: (subscriptionId: string) =>
    apiClient.get<SubscriptionDetail>(`${root}/subscriptions/${subscriptionId}/detail`),

  lifecycleCommand: (subscriptionId: string, command: LifecycleCommand, reason: string) =>
    apiClient.post<CommandResult, { reason: string }>(
      `${root}/subscriptions/${subscriptionId}/lifecycle/${command}`,
      { reason },
    ),

  previewChange: (subscriptionId: string, targetPlanVersionId: string, countryCode: string) =>
    apiClient.post<ChangePreview, { targetPlanVersionId: string; countryCode: string }>(
      `${root}/subscriptions/${subscriptionId}/change-preview`,
      { targetPlanVersionId, countryCode },
    ),

  executeChange: (
    subscriptionId: string,
    targetPlanVersionId: string,
    countryCode: string,
    reason: string,
  ) =>
    apiClient.post<
      CommandResult,
      { targetPlanVersionId: string; countryCode: string; reason: string }
    >(`${root}/subscriptions/${subscriptionId}/changes`, {
      targetPlanVersionId,
      countryCode,
      reason,
    }),

  provision: (subscriptionId: string) =>
    apiClient.post<ProvisioningJob & Record<string, unknown>, Record<string, never>>(
      `${root}/subscriptions/${subscriptionId}/provision`,
      {},
    ),

  provisioningJobs: (query: { tenantId?: string; status?: string } = {}) =>
    apiClient.get<ProvisioningJob[]>(`${root}/provisioning/jobs${qs(query)}`),

  retryProvisioningJob: (jobId: string) =>
    apiClient.post<ProvisioningJob & Record<string, unknown>, Record<string, never>>(
      `${root}/provisioning/jobs/${jobId}/retry`,
      {},
    ),

  usage: (tenantId: string) =>
    apiClient.get<UsageSnapshot[]>(`${root}/usage${qs({ tenantId })}`),

  audit: (query: {
    tenantId?: string;
    action?: string;
    resourceType?: string;
    page?: number;
    size?: number;
    sort?: string;
    direction?: "ASC" | "DESC";
  } = {}) => apiClient.get<PageResponse<AuditEntry>>(`${root}/audit/v2${qs(query)}`),

  accessCheckV2: () => apiClient.get<AccessCheckV2>(`${root}/access-check/v2`),
};
