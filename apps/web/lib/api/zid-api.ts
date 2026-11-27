/**
 * Zid integration API client (tenant-scoped admin surface).
 *
 * The backend derives the tenant from the authenticated session only;
 * no tenant id is ever sent or accepted from the client. All returned
 * shapes are token-free by construction.
 */
import { apiClient } from "./client";

const BASE = "/api/v1/integrations/zid";

export interface ZidConnectionStatus {
  status: "NOT_LINKED" | "CONNECTED" | "REAUTH_REQUIRED" | "PAUSED" | "FAILED";
  zidStoreId: string | null;
  zidStoreName: string | null;
  authorizationExpiresAt: string | null;
  lastRefreshAt: string | null;
  lastSyncAt: string | null;
  lastSyncError: string | null;
  linkedAt: string | null;
}

export interface ZidLinkStart {
  authorizationUrl: string;
  state: string;
  expiresAt: string;
}

export interface ZidSyncSummary {
  status: string;
  zidStoreId: string | null;
  zidStoreName: string | null;
  lastSyncAt: string | null;
  productCount: number;
  publishedProductCount: number;
  stockCount: number;
  orderCount: number;
  failedWebhookEvents: number;
  recentConflicts: number;
}

export interface ZidAuditRow {
  sync_type: string;
  entity: string | null;
  direction: string;
  status: string;
  items_processed: number;
  items_failed: number;
  error_summary: string | null;
  started_at: string;
  finished_at: string | null;
}

export interface ZidWebhookEventRow {
  zid_store_id: string;
  event_type: string | null;
  status: string;
  attempts: number;
  last_error: string | null;
  received_at: string;
  processed_at: string | null;
}

export interface ZidConfiguration {
  enabled: boolean;
  clientConfigured: boolean;
  webhookConfigured: boolean;
}

export interface ZidSyncResult {
  processed?: number;
  failed?: number;
  stocksMirrored?: number;
  productsProcessed?: number;
  result?: string;
}

export const zidApi = {
  configuration: () => apiClient.get<ZidConfiguration>(`${BASE}/configuration`),
  connection: () => apiClient.get<ZidConnectionStatus>(`${BASE}/connection`),
  summary: () => apiClient.get<ZidSyncSummary>(`${BASE}/summary`),
  audit: () => apiClient.get<ZidAuditRow[]>(`${BASE}/audit`),
  webhookEvents: () => apiClient.get<ZidWebhookEventRow[]>(`${BASE}/webhook-events`),
  startLink: () => apiClient.post<ZidLinkStart>(`${BASE}/link/start`, {}),
  unlink: () => apiClient.delete<{ unlinked: boolean }>(`${BASE}/connection`),
  syncProducts: () => apiClient.post<ZidSyncResult>(`${BASE}/sync/products`, {}),
  syncOrders: () => apiClient.post<ZidSyncResult>(`${BASE}/sync/orders`, {}),
  syncInventory: () => apiClient.post<ZidSyncResult>(`${BASE}/sync/inventory`, {}),
  syncCustomers: () => apiClient.post<ZidSyncResult>(`${BASE}/sync/customers`, {}),
  reconcile: () => apiClient.post<Record<string, number | string>>(`${BASE}/reconciliation`, {}),
};
