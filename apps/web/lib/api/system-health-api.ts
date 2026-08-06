import { apiClient } from "./client";

// ── Types ────────────────────────────────────────────────────────────
export interface SystemService {
  id: string; code: string; name: string; environment: string;
  status: string; ownerName: string | null; criticality: string;
  lastCheckedAt: string | null; lastLatencyMs: number | null;
  lastMessage: string | null;
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

export interface RuntimeMetrics {
  cpuLoadPercent: number;
  memoryUsagePercent: number;
  memoryUsedMb: number;
  memoryMaxMb: number;
  uptimeSeconds: number;
  availableProcessors: number;
  pressureScore: number;
  status: string;
  trackedRows: number;
  auditEventsLastHour: number;
}

export interface DataPressure {
  status: string;
  pressureScore: number;
  trackedRows: number;
  auditEventsLastHour: number;
}

export interface ServiceHealth {
  id: string; code: string; name: string; environment: string;
  status: string; criticality: string; healthScore: number;
  pressureScore: number; riskLevel: string; latencyMs: number | null;
}

export interface TenantHealth {
  tenantId: string; tenantName: string; tenantStatus: string;
  healthScore: number; pressureScore: number; riskLevel: string;
  users: number; organizations: number; memberships: number; invoices: number;
}

export interface RiskForecastPoint {
  label: string; riskLevel: string; prediction: string;
}

export interface HealthActionDescriptor {
  scope: string; code: string; label: string; description: string;
}

export interface HealthActionInput {
  scope: "PLATFORM" | "SERVICE" | "TENANT";
  actionCode: string;
  targetId?: string;
}

export interface HealthActionResult {
  actionCode: string;
  result: string;
  message: string;
}

// ── API ──────────────────────────────────────────────────────────────
const root = "/api/v1/system-health";

export const systemHealthApi = {
  snapshot: () => apiClient.get<PlatformHealth>(root, { cache: "no-store" }),
  execute: (body: HealthActionInput) => apiClient.post<HealthActionResult, typeof body>(`${root}/actions`, body),
  systems: () => apiClient.get<SystemService[]>("/api/v1/executive/systems", { cache: "no-store" }),
};
