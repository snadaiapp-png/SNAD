/**
 * Workflow Engine API client — typed wrapper for /api/v1/workflows/* endpoints.
 * Business rules and authorization remain server-side.
 */

import { apiClient } from "./client";

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
  definitionFamilyId: string;
  engineGeneration: "LEGACY" | "Y2" | string;
  publicationState: "DRAFT" | "PUBLISHED" | "RETIRED" | string;
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
  requestedFromEmployeeId: string | null;
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

export type WorkflowStepType =
  | "START"
  | "HUMAN_TASK"
  | "APPROVAL"
  | "CONDITION"
  | "SYSTEM_ACTION"
  | "PARALLEL_FORK"
  | "PARALLEL_JOIN"
  | "CALL_WORKFLOW"
  | "NOTIFICATION"
  | "END";

export interface WorkflowStepResponse {
  id: string;
  workflowDefinitionId: string;
  stepKey: string;
  name: string;
  stepType: WorkflowStepType | string;
  sequenceOrder: number;
  configuration: string;
  slaHours: number;
  requiredCapability: string;
  requiredRole: string;
  version: number;
}

export interface CreateWorkflowStepRequest {
  stepKey: string;
  name: string;
  stepType: WorkflowStepType;
  sequenceOrder: number;
  configuration: string;
  slaHours: number;
  requiredCapability: string;
  requiredRole: string;
}

export interface WorkflowTransitionResponse {
  id: string;
  fromStepId: string;
  toStepId: string;
  transitionKey: string;
  outcome: string;
  priority: number;
}

export interface CreateWorkflowTransitionRequest {
  fromStepId: string;
  toStepId: string;
  transitionKey: string;
  outcome: string;
  conditionAst: string;
  priority: number;
  metadata: string;
}

const BASE = "/api/v1/workflows";

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

  approveRequest: (id: string, expectedVersion: number, comments: string = "") =>
    apiClient.post<WorkflowApprovalResponse>(`${BASE}/approvals/${id}/approve`, { expectedVersion, comments }),

  rejectRequest: (id: string, expectedVersion: number, comments: string = "") =>
    apiClient.post<WorkflowApprovalResponse>(`${BASE}/approvals/${id}/reject`, { expectedVersion, comments }),

  // ===== Monitoring =====
  getMonitoringHealth: () =>
    apiClient.get<WorkflowMonitoringHealthResponse>(`${BASE}/monitoring/health`),

  triggerSlaCheck: () =>
    apiClient.post<WorkflowMonitoringHealthResponse>(`${BASE}/monitoring/check-sla`),

  // ===== Y2 WorkItems =====
  listMyWorkItems: (limit = 50) =>
    apiClient.get<WorkflowWorkItemResponse[]>(`${BASE}/work-items/mine?limit=${limit}`),

  listPoolWorkItems: (limit = 50) =>
    apiClient.get<WorkflowWorkItemResponse[]>(`${BASE}/work-items/pool?limit=${limit}`),

  claimWorkItem: (id: string, expectedVersion: number) =>
    apiClient.post<WorkflowWorkItemResponse>(`${BASE}/work-items/${id}/claim`, { expectedVersion }),

  releaseWorkItem: (id: string, expectedVersion: number) =>
    apiClient.post<WorkflowWorkItemResponse>(`${BASE}/work-items/${id}/release`, { expectedVersion }),

  completeWorkItem: (id: string, expectedVersion: number) =>
    apiClient.post<WorkflowWorkItemResponse>(`${BASE}/work-items/${id}/complete`, { expectedVersion }),

  reassignWorkItem: (id: string, newAssigneeEmployeeId: string, expectedVersion: number, reason: string) =>
    apiClient.post<WorkflowWorkItemResponse>(`${BASE}/work-items/${id}/reassign`,
      { newAssigneeEmployeeId, expectedVersion, reason }),

  // ===== Y2 Definition/version designer =====
  publishDefinition: (id: string, expectedVersion: number) =>
    apiClient.post<WorkflowDefinitionResponse>(`${BASE}/definitions/${id}/publish`, { expectedVersion }),

  createNextDraft: (id: string) =>
    apiClient.post<WorkflowDefinitionResponse>(`${BASE}/definitions/${id}/next-draft`, {}),

  validateDefinition: (id: string) =>
    apiClient.post<WorkflowValidationResponse>(`${BASE}/definitions/${id}/validate`, {}),

  simulateDefinition: (id: string) =>
    apiClient.post<WorkflowSimulationResponse>(`${BASE}/definitions/${id}/simulate`, {}),

  getDefinitionSteps: (id: string) =>
    apiClient.get<WorkflowStepResponse[]>(`${BASE}/definitions/${id}/steps`),

  getDefinitionTransitions: (id: string) =>
    apiClient.get<WorkflowTransitionResponse[]>(`${BASE}/definitions/${id}/transitions`),

  addDefinitionStep: (id: string, data: CreateWorkflowStepRequest) =>
    apiClient.post<WorkflowStepResponse>(`${BASE}/definitions/${id}/steps`, data),

  createDefinitionTransition: (id: string, data: CreateWorkflowTransitionRequest) =>
    apiClient.post<WorkflowTransitionResponse>(`${BASE}/definitions/${id}/transitions`, data),

  // ===== Y2 Incidents =====
  listIncidents: (limit = 50) =>
    apiClient.get<WorkflowIncidentResponse[]>(`${BASE}/incidents?limit=${limit}`),

  acknowledgeIncident: (id: string) =>
    apiClient.post<WorkflowIncidentResponse>(`${BASE}/incidents/${id}/acknowledge`, {}),

  resolveIncident: (id: string, expectedVersion: number, resolution: string) =>
    apiClient.post<WorkflowIncidentResponse>(`${BASE}/incidents/${id}/resolve`, { expectedVersion, resolution }),
};

export interface WorkflowWorkItemResponse {
  id: string;
  workflowInstanceId: string;
  workflowStepInstanceId: string;
  type: "HUMAN_TASK" | "APPROVAL";
  status: string;
  assigneeEmployeeId: string | "";
  claimedByEmployeeId: string | "";
  assignmentMode: "DIRECT" | "WORK_POOL";
  title: string;
  priority: number;
  dueAt: string | "";
  version: number;
}

export interface WorkflowValidationResponse {
  valid: boolean;
  errors: { code: string; message: string; stepId: string }[];
}

export interface WorkflowSimulationResponse {
  valid: boolean;
  simulated: boolean;
  visitedStepIds: string[];
  notes: string[];
}

export interface WorkflowIncidentResponse {
  id: string;
  workflowInstanceId: string;
  source: string;
  severity: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  failureCategory: string;
  status: string;
  resolution: string;
  createdAt: string;
  version: number;
}
