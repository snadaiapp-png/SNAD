import { apiClient, type ApiClient } from "./client";

export type CrmIntegrationStatus =
  | "PENDING"
  | "ACCEPTED"
  | "COMPLETED"
  | "REJECTED"
  | "UNAVAILABLE"
  | "TIMED_OUT"
  | "POLICY_DENIED"
  | "UNSAFE_OUTPUT";

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
  dataClassification: string;
  payload: Record<string, unknown>;
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
