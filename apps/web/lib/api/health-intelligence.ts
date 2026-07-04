import { apiClient } from "./client";

export interface RuntimeMetrics {
  cpuLoadPercent: number;
  memoryUsagePercent: number;
  memoryUsedMb: number;
  memoryMaxMb: number;
  uptimeSeconds: number;
  availableProcessors: number;
}

export interface DataPressure {
  pressureScore: number;
  status: string;
  trackedRows: number;
  auditEventsLastHour: number;
  failedAuditEventsLastHour: number;
  openInvoices: number;
  activeUsers: number;
  message: string;
}

export interface ServiceHealth {
  id: string;
  code: string;
  name: string;
  environment: string;
  status: string;
  criticality: string;
  healthScore: number;
  pressureScore: number;
  riskLevel: string;
  latencyMs: number | null;
  lastMessage: string | null;
  lastCheckedAt: string | null;
  predictedStatus: string;
}

export interface TenantHealth {
  tenantId: string;
  tenantName: string;
  tenantStatus: string;
  healthScore: number;
  pressureScore: number;
  riskLevel: string;
  users: number;
  organizations: number;
  memberships: number;
  invoices: number;
  openInvoices: number;
  seatCapacity: number;
  seatUtilizationPercent: number;
  trackedRecords: number;
  prediction: string;
}

export interface RiskForecastPoint {
  horizonMinutes: number;
  riskScore: number;
  riskLevel: string;
  label: string;
}

export interface HealthActionDescriptor {
  code: string;
  scope: string;
  title: string;
  description: string;
  requiresTarget: boolean;
}

export interface PlatformHealth {
  generatedAt: string;
  overallStatus: string;
  healthScore: number;
  riskLevel: string;
  predictionSummary: string;
  runtime: RuntimeMetrics;
  dataPressure: DataPressure;
  services: ServiceHealth[];
  tenants: TenantHealth[];
  forecast: RiskForecastPoint[];
  availableActions: HealthActionDescriptor[];
}

export interface HealthActionInput {
  scope: "PLATFORM" | "SERVICE" | "TENANT";
  targetId?: string;
  action: "RUN_DIAGNOSTICS" | "AUTO_HEAL" | "MARK_MAINTENANCE" | "RESTORE_OPERATION" | "REFRESH_TENANT_HEALTH";
  reason: string;
}

export interface HealthActionResult {
  action: string;
  scope: string;
  targetId: string | null;
  status: string;
  message: string;
  executedAt: string;
  snapshot: PlatformHealth;
}

export const healthIntelligenceApi = {
  snapshot: () => apiClient.get<PlatformHealth>("/api/v1/control-plane/health"),
  execute: (body: HealthActionInput) =>
    apiClient.post<HealthActionResult, HealthActionInput>("/api/v1/control-plane/health/actions", body),
};
