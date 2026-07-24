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
  integrationType: string;
  status: CrmIntegrationStatus;
  externalReference: string | null;
  correlationId: string;
  idempotencyKey: string;
  requestedAt: string;
  expiresAt: string;
  errorCode: string | null;
}

export interface CrmAiInsightRequest {
  capability: "CUSTOMER_SUMMARY" | "NEXT_BEST_ACTION" | "SCORING";
  sourceEntityType: string;
  sourceEntityId: string;
  sourceEntityVersion: number;
  userIntent?: string;
}

export interface CrmConfirmRequest {
  expectedEntityVersion: number;
}

export interface CrmRejectRequest {
  reason?: string;
}

/** Browser → Vercel BFF → Render — never calls Render or AI Gateway directly. */
export async function requestCrmAiInsight(
  request: CrmAiInsightRequest,
  idempotencyKey: string,
  client: ApiClient = apiClient,
): Promise<CrmIntegrationRequestStatus> {
  if (!idempotencyKey.trim()) throw new Error("idempotencyKey is required");
  return client.post<CrmIntegrationRequestStatus, CrmAiInsightRequest>(
    "/api/v2/crm/integrations/ai",
    request,
    { context: { headers: { "Idempotency-Key": idempotencyKey } }, cache: "no-store" },
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

/** Confirm requires If-Match (integration request version) and CRM.AI.CONFIRM capability. */
export async function confirmCrmAiRecommendation(
  requestId: string,
  idempotencyKey: string,
  ifMatch: string,
  expectedEntityVersion: number,
  client: ApiClient = apiClient,
): Promise<CrmIntegrationRequestStatus> {
  if (!idempotencyKey.trim()) throw new Error("idempotencyKey is required");
  if (!ifMatch.trim()) throw new Error("If-Match header is required");
  return client.post<CrmIntegrationRequestStatus, CrmConfirmRequest>(
    `/api/v2/crm/integrations/${encodeURIComponent(requestId)}/confirm`,
    { expectedEntityVersion },
    { context: { headers: { "Idempotency-Key": idempotencyKey, "If-Match": ifMatch } }, cache: "no-store" },
  );
}

export async function rejectCrmAiRecommendation(
  requestId: string,
  idempotencyKey: string,
  reason?: string,
  client: ApiClient = apiClient,
): Promise<CrmIntegrationRequestStatus> {
  if (!idempotencyKey.trim()) throw new Error("idempotencyKey is required");
  return client.post<CrmIntegrationRequestStatus, CrmRejectRequest>(
    `/api/v2/crm/integrations/${encodeURIComponent(requestId)}/reject`,
    { reason },
    { context: { headers: { "Idempotency-Key": idempotencyKey } }, cache: "no-store" },
  );
}
