/**
 * Senior Management API client — typed wrapper for all /api/v1/management/* endpoints.
 *
 * Consumes the backend APIs. Does NOT duplicate business logic.
 */

import { apiClient } from "./client";

// ── Types ────────────────────────────────────────────────────────────

export interface CommandCenterDashboard {
  healthScore: number;
  strategyScore: number;
  kpiScore: number;
  decisionScore: number;
  riskScore: number;
  issueScore: number;
  escalationScore: number;
  totalObjectives: number;
  activeObjectives: number;
  atRiskObjectives: number;
  offTrackObjectives: number;
  achievedObjectives: number;
  totalKpis: number;
  onTrackKpis: number;
  atRiskKpis: number;
  offTrackKpis: number;
  noDataKpis: number;
  pendingDecisions: number;
  overdueDecisions: number;
  criticalRisks: number;
  highRisks: number;
  totalRisks: number;
  openIssues: number;
  criticalIssues: number;
  totalIssues: number;
  activeEscalations: number;
  overdueEscalations: number;
  totalEscalations: number;
  activeAlerts: number;
  // Governed-systems overviews (added in v20260815.7 — Executive Command Center
  // now aggregates CRM, Finance, Analytics, Workflow, and Module Registry).
  financeOverview?: Record<string, unknown>;
  moduleGovernance?: Array<Record<string, unknown>>;
  crmOverview?: Record<string, unknown>;
  analyticsOverview?: Record<string, unknown>;
  workflowHealth?: Record<string, unknown>;
  generatedAt: string;
}

export interface ObjectiveResponse {
  id: string;
  code: string;
  title: string;
  status: string;
  priority: string;
  progressPct: number;
  ownerUserId?: string;
  periodStart: string;
  periodEnd: string;
}

export interface RiskResponse {
  id: string;
  code: string;
  title: string;
  status: string;
  severity: string;
  riskScore: number;
  probability: number;
  impact: number;
}

export interface IssueResponse {
  id: string;
  code: string;
  title: string;
  status: string;
  severity: string;
  priority: string;
}

export interface DecisionResponse {
  id: string;
  decisionNumber: string;
  title: string;
  status: string;
  priority: string;
}

export interface EscalationResponse {
  id: string;
  code: string;
  sourceEntityType: string;
  sourceEntityId: string;
  reason: string;
  status: string;
  severity: string;
  escalationLevel: number;
}

export interface AlertResponse {
  id: string;
  type: string;
  severity: string;
  sourceEntityType: string;
  sourceEntityId: string;
  title: string;
  status: string;
}

export interface InsightResponse {
  id: string;
  type: string;
  title: string;
  description: string;
  confidence: string;
  modelName: string;
  advisory: boolean;
  status: string;
}

// ── API Functions ────────────────────────────────────────────────────

const BASE = "/api/v1/management";

export const managementApi = {
  // Command Center
  getDashboard: () =>
    apiClient.get<CommandCenterDashboard>(`${BASE}/command-center`),

  snapshotHealth: () =>
    apiClient.post<{ healthScore: number; snapshotAt: string }>(`${BASE}/command-center/snapshot`),

  // Objectives
  listObjectives: (limit = 50) =>
    apiClient.get<ObjectiveResponse[]>(`${BASE}/objectives?limit=${limit}`),

  createObjective: (data: {
    code: string; title: string; description?: string;
    priority: string; periodStart: string; periodEnd: string;
  }) =>
    apiClient.post<ObjectiveResponse>(`${BASE}/objectives`, data),

  activateObjective: (id: string) =>
    apiClient.post<ObjectiveResponse>(`${BASE}/objectives/${id}/activate`),

  achieveObjective: (id: string) =>
    apiClient.post<ObjectiveResponse>(`${BASE}/objectives/${id}/achieve`),

  // KPIs
  listKpis: (limit = 50) =>
    apiClient.get<{ id: string; code: string; name: string; status: string }[]>(`${BASE}/kpis?limit=${limit}`),

  // Decisions
  listDecisions: (limit = 50) =>
    apiClient.get<DecisionResponse[]>(`${BASE}/decisions?limit=${limit}`),

  createDecision: (data: {
    decisionNumber: string; title: string; description?: string;
    rationale?: string; category?: string; priority: string;
    impact?: string; expectedOutcome?: string; dueDate?: string;
  }) =>
    apiClient.post<DecisionResponse>(`${BASE}/decisions`, data),

  approveDecision: (id: string) =>
    apiClient.post<DecisionResponse>(`${BASE}/decisions/${id}/approve`),

  // Risks
  listRisks: (limit = 50) =>
    apiClient.get<RiskResponse[]>(`${BASE}/risks?limit=${limit}`),

  createRisk: (data: {
    code: string; title: string; description?: string;
    category?: string; probability: number; impact: number;
    dueDate?: string;
  }) =>
    apiClient.post<RiskResponse>(`${BASE}/risks`, data),

  // Issues
  listIssues: (limit = 50) =>
    apiClient.get<IssueResponse[]>(`${BASE}/issues?limit=${limit}`),

  createIssue: (data: {
    code: string; title: string; description?: string;
    severity: string; priority: string; source?: string;
    impact?: string; dueDate?: string;
  }) =>
    apiClient.post<IssueResponse>(`${BASE}/issues`, data),

  // Escalations
  listEscalations: (limit = 50) =>
    apiClient.get<EscalationResponse[]>(`${BASE}/escalations?limit=${limit}`),

  acknowledgeEscalation: (id: string) =>
    apiClient.post<EscalationResponse>(`${BASE}/escalations/${id}/acknowledge`),

  resolveEscalation: (id: string, resolution: string) =>
    apiClient.post<EscalationResponse>(`${BASE}/escalations/${id}/resolve`, { resolution }),

  // Alerts
  listOpenAlerts: (limit = 50) =>
    apiClient.get<AlertResponse[]>(`${BASE}/alerts/open?limit=${limit}`),

  acknowledgeAlert: (id: string) =>
    apiClient.post<AlertResponse>(`${BASE}/alerts/${id}/acknowledge`),

  resolveAlert: (id: string, resolution: string) =>
    apiClient.post<AlertResponse>(`${BASE}/alerts/${id}/resolve`, { resolution }),

  // AI Intelligence
  generateSummary: () =>
    apiClient.post<InsightResponse>(`${BASE}/intelligence/summary`),

  detectAnomalies: () =>
    apiClient.post<InsightResponse[]>(`${BASE}/intelligence/anomalies`),

  recommendAction: () =>
    apiClient.post<InsightResponse>(`${BASE}/intelligence/recommend`),

  listInsights: (limit = 20) =>
    apiClient.get<InsightResponse[]>(`${BASE}/intelligence?limit=${limit}`),
};
