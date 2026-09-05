/**
 * Workflow Engine API client — typed wrapper for all /api/v1/workflows/* endpoints.
 *
 * Mirrors the management-api.ts pattern. Consumes backend APIs only.
 * Does NOT duplicate business logic.
 */

import { apiClient } from "./client";

// ── Types ────────────────────────────────────────────────────────────

export interface WorkflowDefinitionResponse {
  id: string;
  code: string;
  name: string;
  status: string;
  triggerType: string;
  module: string;
  version: number;
  versionLock: number;
  createdBy: string;
}

export interface WorkflowInstanceResponse {
  id: string;
  workflowDefinitionId: string;
  workflowVersion: number;
  businessEntityType: string;
  businessEntityId: string;
  status: string;
  currentStepKey: string;
  startedBy: string;
  version: number;
}

export interface WorkflowApprovalResponse {
  id: string;
  workflowInstanceId: string;
  workflowStepInstanceId: string;
  requestedFromUserId: string;
  status: string;
  decision: string;
  comments: string;
  version: number;
}

export interface WorkflowMonitoringHealthResponse {
  status: string;
  tenantId: string;
  overdueSteps: number;
  overdueApprovals: number;
  totalBreaches: number;
}

export interface CreateDefinitionRequest {
  code: string;
  name: string;
  description?: string;
  module?: string;
  triggerType?: string;
}

export interface StartWorkflowRequest {
  workflowDefinitionId: string;
  businessEntityType: string;
  businessEntityId: string;
  correlationId?: string;
}

// ── Constants ────────────────────────────────────────────────────────

const BASE = "/api/v1/workflows";

// ── Client ───────────────────────────────────────────────────────────

export const workflowApi = {
  // ===== Definitions =====
  listDefinitions: (limit = 50) =>
    apiClient.get<WorkflowDefinitionResponse[]>(`${BASE}/definitions?limit=${limit}`),

  getDefinition: (id: string) =>
    apiClient.get<WorkflowDefinitionResponse>(`${BASE}/definitions/${id}`),

  createDefinition: (data: CreateDefinitionRequest) =>
    apiClient.post<WorkflowDefinitionResponse>(`${BASE}/definitions`, data),

  activateDefinition: (id: string) =>
    apiClient.post<WorkflowDefinitionResponse>(`${BASE}/definitions/${id}/activate`),

  deactivateDefinition: (id: string) =>
    apiClient.post<WorkflowDefinitionResponse>(`${BASE}/definitions/${id}/deactivate`),

  archiveDefinition: (id: string) =>
    apiClient.post<WorkflowDefinitionResponse>(`${BASE}/definitions/${id}/archive`),

  // ===== Instances =====
  listInstances: (limit = 50) =>
    apiClient.get<WorkflowInstanceResponse[]>(`${BASE}/instances?limit=${limit}`),

  getInstance: (id: string) =>
    apiClient.get<WorkflowInstanceResponse>(`${BASE}/instances/${id}`),

  startWorkflow: (data: StartWorkflowRequest) =>
    apiClient.post<WorkflowInstanceResponse>(`${BASE}/instances`, data),

  pauseInstance: (id: string) =>
    apiClient.post<WorkflowInstanceResponse>(`${BASE}/instances/${id}/pause`),

  resumeInstance: (id: string) =>
    apiClient.post<WorkflowInstanceResponse>(`${BASE}/instances/${id}/resume`),

  cancelInstance: (id: string, reason: string = "") =>
    apiClient.post<WorkflowInstanceResponse>(`${BASE}/instances/${id}/cancel`, { reason }),

  // ===== Approvals =====
  listPendingApprovals: (limit = 50) =>
    apiClient.get<WorkflowApprovalResponse[]>(`${BASE}/approvals?limit=${limit}`),

  listMyPendingApprovals: (limit = 50) =>
    apiClient.get<WorkflowApprovalResponse[]>(`${BASE}/approvals/pending?limit=${limit}`),

  approveRequest: (id: string, comments: string = "") =>
    apiClient.post<WorkflowApprovalResponse>(`${BASE}/approvals/${id}/approve`, { comments }),

  rejectRequest: (id: string, comments: string = "") =>
    apiClient.post<WorkflowApprovalResponse>(`${BASE}/approvals/${id}/reject`, { comments }),

  // ===== Monitoring =====
  getMonitoringHealth: () =>
    apiClient.get<WorkflowMonitoringHealthResponse>(`${BASE}/monitoring/health`),

  triggerSlaCheck: () =>
    apiClient.post<WorkflowMonitoringHealthResponse>(`${BASE}/monitoring/check-sla`),
};
