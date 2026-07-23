import { apiClient, type ApiClient } from "./client";

export type CrmIntegrationStatus =
  | "PENDING"
  | "DISPATCHED"
  | "ACCEPTED"
  | "RUNNING"
  | "COMPLETED"
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

/** Uses the existing authenticated /api/platform BFF; the browser never calls Render directly. */
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

/** Confirm an AI recommendation with human confirmation. Requires CRM.AI.CONFIRM capability. */
export async function confirmCrmAiRecommendation(
  requestId: string,
  idempotencyKey: string,
  expectedEntityVersion: number,
  client: ApiClient = apiClient,
): Promise<CrmIntegrationRequestStatus> {
  if (!idempotencyKey.trim()) throw new Error("idempotencyKey is required");
  return client.post<CrmIntegrationRequestStatus, { expectedEntityVersion: number }>(
    `/api/v2/crm/integrations/${encodeURIComponent(requestId)}/confirm`,
    { expectedEntityVersion },
    { context: { headers: { "Idempotency-Key": idempotencyKey } }, cache: "no-store" },
  );
}

/** Reject an AI recommendation. Requires CRM.AI.CONFIRM capability. */
export async function rejectCrmAiRecommendation(
  requestId: string,
  idempotencyKey: string,
  reason?: string,
  client: ApiClient = apiClient,
): Promise<CrmIntegrationRequestStatus> {
  if (!idempotencyKey.trim()) throw new Error("idempotencyKey is required");
  return client.post<CrmIntegrationRequestStatus, { reason?: string }>(
    `/api/v2/crm/integrations/${encodeURIComponent(requestId)}/reject`,
    { reason },
    { context: { headers: { "Idempotency-Key": idempotencyKey } }, cache: "no-store" },
  );
}
