import { apiClient, type ApiClient } from "./client";

export type CrmIntegrationStatus =
  | "PENDING"
  | "DISPATCHED"
  | "ACCEPTED"
  | "RUNNING"
  | "COMPLETED"
  | "RECOMMENDATION_AVAILABLE"
  | "CONFIRMED"
  | "EXECUTING"
  | "EXECUTED"
  | "EXECUTION_REJECTED"
  | "REJECTED"
  | "POLICY_DENIED"
  | "UNSAFE_OUTPUT"
  | "TIMED_OUT"
  | "UNAVAILABLE"
  | "CANCELLED"
  | "EXPIRED";

export interface CrmIntegrationRequestStatus {
  id: string;
  tenantId: string;
  actorId?: string;
  integrationType: "AI" | "WORKFLOW" | string;
  status: CrmIntegrationStatus;
  externalReference: string | null;
  correlationId: string;
  idempotencyKey: string;
  sourceEntityType?: string;
  sourceEntityId?: string;
  sourceEntityVersion?: number;
  payload?: Record<string, unknown> | null;
  resultPayload?: Record<string, unknown> | null;
  requestedAt: string;
  expiresAt: string;
  errorCode: string | null;
  version: number;
}

export interface CrmAiInsightRequest {
  capability: "CUSTOMER_SUMMARY" | "NEXT_BEST_ACTION" | "SCORING";
  sourceEntityType: string;
  sourceEntityId: string;
  sourceEntityVersion: number;
  userIntent?: string;
}

export interface CrmWorkflowDispatchRequest {
  workflowType: "ASSIGNMENT" | "OPPORTUNITY_APPROVAL" | "REMINDER" | "ESCALATION";
  sourceEntityType: string;
  sourceEntityId: string;
  sourceEntityVersion: number;
  payload?: Record<string, unknown>;
}

export interface CrmConfirmRequest {
  expectedEntityVersion: number;
}

export interface CrmRejectRequest {
  reason?: string;
}

export interface CrmWorkflowCancelRequest {
  reason?: string;
}

function mutationHeaders(idempotencyKey: string, ifMatch?: string): Record<string, string> {
  if (!idempotencyKey.trim()) throw new Error("idempotencyKey is required");
  const headers: Record<string, string> = { "Idempotency-Key": idempotencyKey };
  if (ifMatch !== undefined) {
    if (!ifMatch.trim()) throw new Error("If-Match header is required");
    headers["If-Match"] = ifMatch;
  }
  return headers;
}

/** Browser → same-origin /api/platform BFF → platform backend. */
export async function requestCrmAiInsight(
  request: CrmAiInsightRequest,
  idempotencyKey: string,
  client: ApiClient = apiClient,
): Promise<CrmIntegrationRequestStatus> {
  return client.post<CrmIntegrationRequestStatus, CrmAiInsightRequest>(
    "/api/v2/crm/integrations/ai",
    request,
    { context: { headers: mutationHeaders(idempotencyKey) }, cache: "no-store" },
  );
}

export async function dispatchCrmWorkflow(
  request: CrmWorkflowDispatchRequest,
  idempotencyKey: string,
  client: ApiClient = apiClient,
): Promise<CrmIntegrationRequestStatus> {
  return client.post<CrmIntegrationRequestStatus, CrmWorkflowDispatchRequest>(
    "/api/v2/crm/integrations/workflows",
    request,
    { context: { headers: mutationHeaders(idempotencyKey) }, cache: "no-store" },
  );
}

export async function getCrmIntegrationStatus(
  requestId: string,
  client: ApiClient = apiClient,
): Promise<CrmIntegrationRequestStatus> {
  if (!requestId.trim()) throw new Error("requestId is required");
  return client.get<CrmIntegrationRequestStatus>(
    `/api/v2/crm/integrations/${encodeURIComponent(requestId)}`,
    { cache: "no-store" },
  );
}

export async function getCrmWorkflowStatus(
  requestId: string,
  client: ApiClient = apiClient,
): Promise<CrmIntegrationRequestStatus> {
  if (!requestId.trim()) throw new Error("requestId is required");
  return client.get<CrmIntegrationRequestStatus>(
    `/api/v2/crm/integrations/workflows/${encodeURIComponent(requestId)}`,
    { cache: "no-store" },
  );
}

export async function confirmCrmAiRecommendation(
  requestId: string,
  idempotencyKey: string,
  ifMatch: string,
  expectedEntityVersion: number,
  client: ApiClient = apiClient,
): Promise<CrmIntegrationRequestStatus> {
  return client.post<CrmIntegrationRequestStatus, CrmConfirmRequest>(
    `/api/v2/crm/integrations/${encodeURIComponent(requestId)}/confirm`,
    { expectedEntityVersion },
    {
      context: { headers: mutationHeaders(idempotencyKey, ifMatch) },
      cache: "no-store",
    },
  );
}

export async function rejectCrmAiRecommendation(
  requestId: string,
  idempotencyKey: string,
  ifMatch: string,
  reason?: string,
  client: ApiClient = apiClient,
): Promise<CrmIntegrationRequestStatus> {
  return client.post<CrmIntegrationRequestStatus, CrmRejectRequest>(
    `/api/v2/crm/integrations/${encodeURIComponent(requestId)}/reject`,
    { reason },
    {
      context: { headers: mutationHeaders(idempotencyKey, ifMatch) },
      cache: "no-store",
    },
  );
}

export async function cancelCrmWorkflow(
  requestId: string,
  idempotencyKey: string,
  ifMatch: string,
  reason?: string,
  client: ApiClient = apiClient,
): Promise<CrmIntegrationRequestStatus> {
  return client.post<CrmIntegrationRequestStatus, CrmWorkflowCancelRequest>(
    `/api/v2/crm/integrations/workflows/${encodeURIComponent(requestId)}/cancel`,
    { reason },
    {
      context: { headers: mutationHeaders(idempotencyKey, ifMatch) },
      cache: "no-store",
    },
  );
}
