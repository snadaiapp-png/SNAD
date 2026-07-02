import { apiClient } from "./client";

export interface AuditEntry {
  id: string;
  action: string;
  resourceType: string;
  resourceId: string | null;
  reason: string | null;
  result: "SUCCESS" | "FAILURE";
  createdAt: string;
}

export interface ExecutiveDashboard {
  totalTenants: number;
  activeTenants: number;
  trialTenants: number;
  suspendedTenants: number;
  totalUsers: number;
  activeUsers: number;
  operationalServices: number;
  degradedServices: number;
  recentActivity: AuditEntry[];
}

export interface ManagedTenant {
  id: string;
  name: string;
  legalName: string | null;
  subdomain: string;
  status: string;
  billingEmail: string | null;
  countryCode: string | null;
  locale: string;
  timezone: string;
  currencyCode: string;
  trialEndsAt: string | null;
  suspensionReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SystemService {
  id: string;
  code: string;
  name: string;
  environment: string;
  status: string;
  ownerName: string | null;
  criticality: string;
  lastCheckedAt: string | null;
  lastLatencyMs: number | null;
  lastMessage: string | null;
}

export const platformOperationsApi = {
  dashboard(): Promise<ExecutiveDashboard> {
    return apiClient.get<ExecutiveDashboard>("/api/v1/control-plane/dashboard");
  },
  tenants(): Promise<ManagedTenant[]> {
    return apiClient.get<ManagedTenant[]>("/api/v1/control-plane/tenants");
  },
  systems(): Promise<SystemService[]> {
    return apiClient.get<SystemService[]>("/api/v1/control-plane/systems");
  },
};
