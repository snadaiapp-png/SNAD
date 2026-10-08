import { apiClient } from "./client";

/** G4-T9: backend-authenticated payroll transport. Never supply tenant/user ids. */
const ROOT = "/api/v2/hr/payroll";
export type PayrollStatus = "DRAFT" | "CALCULATED" | "REVIEWED" | "APPROVED" | "EXPORTED" | "CANCELLED";
export interface PayrollRun {
  id: string; legalEntityId: string; periodStart: string; periodEnd: string;
  currencyCode: string; status: PayrollStatus; sourceCutoffAt: string; version: number;
}
export interface PayrollItem {
  id: string; employmentId: string; compensationPackageId: string; timesheetId: string;
  baseAmount: number; grossAmount: number; deductionTotal: number; netAmount: number;
  status: string; exceptionCode: string | null; version: number;
}
export interface PayrollCreateRequest {
  legalEntityId: string; periodStart: string; periodEnd: string;
  currencyCode: string; sourceCutoffAt: string;
}
export interface PayrollVersionRequest { expectedVersion: number; reason: string }
export type PayrollMutation = "calculate" | "recalculate" | "review" | "approve" | "export";
const safeId = (id: string) => encodeURIComponent(id);
const opts = () => ({ context: { headers: { "Idempotency-Key": globalThis.crypto.randomUUID() } } });
export const hrPayrollApi = {
  listRuns: () => apiClient.get<PayrollRun[]>(`${ROOT}/runs`),
  getRun: (runId: string) => apiClient.get<PayrollRun>(`${ROOT}/runs/${safeId(runId)}`),
  listItems: (runId: string) => apiClient.get<PayrollItem[]>(`${ROOT}/runs/${safeId(runId)}/items`),
  getItem: (runId: string, itemId: string) => apiClient.get<PayrollItem>(`${ROOT}/runs/${safeId(runId)}/items/${safeId(itemId)}`),
  createRun: (request: PayrollCreateRequest) => apiClient.post<PayrollRun>(`${ROOT}/runs`, request, opts()),
  mutate: (runId: string, action: PayrollMutation, expectedVersion: number, reason: string) =>
    apiClient.post<unknown>(`${ROOT}/runs/${safeId(runId)}/${action}`,
      action === "export" ? { expectedVersion } : { expectedVersion, reason }, opts()),
};
