import { apiClient } from "./client";

// ── Types ────────────────────────────────────────────────────────────
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

export interface PlatformHealth {
  generatedAt: string;
  overallStatus: string;
  healthScore: number;
  riskLevel: string;
  predictionSummary: string;
  partial: boolean;
  dataCompletenessScore: number;
  degradedComponents: string[];
  collectionErrors: CollectionError[];
  runtime: RuntimeMetrics;
  dataPressure: DataPressure;
  services: ServiceHealth[];
  tenants: TenantHealth[];
  forecast: RiskForecastPoint[];
  availableActions: HealthActionDescriptor[];
}

export interface CollectionError {
  component: string;
  code: string;
  message: string;
  correlationId: string;
  timestamp: string;
}

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
  scope: "PLATFORM" | "SERVICE" | "TENANT";
  title: string;
  description: string;
  requiresTarget: boolean;
}

export interface HealthActionInput {
  scope: "PLATFORM" | "SERVICE" | "TENANT";
  targetId?: string;
  action: string;
  reason: string;
}

export interface HealthActionResult {
  action: string;
  scope: "PLATFORM" | "SERVICE" | "TENANT";
  targetId: string | null;
  status: string;
  message: string;
  executedAt: string;
  snapshot: PlatformHealth;
}

// ── API ──────────────────────────────────────────────────────────────
const root = "/api/v1/system-health";

export const systemHealthApi = {
  snapshot: () => apiClient.get<PlatformHealth>(root, { cache: "no-store" }),
  execute: (body: HealthActionInput) =>
    apiClient.post<HealthActionResult, HealthActionInput>(`${root}/actions`, body),
  systems: () => apiClient.get<SystemService[]>(`${root}/systems`, { cache: "no-store" }),
};
