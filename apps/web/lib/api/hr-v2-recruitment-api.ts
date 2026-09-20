/**
 * HR v2 Recruitment & Onboarding API client (G1-T11).
 * ---------------------------------------------------------------------------
 * Source of truth: apps/sanad-platform/src/main/java/com/sanad/platform/hr/api/v2/
 *   - recruitment/HrRecruitmentV2Controller.java
 *   - onboarding/HrOnboardingV2Controller.java
 *
 * Contract:
 *   - Base path: /api/platform/api/v2/hr/recruitment and /.../onboarding
 *     (BFF-prefixed; the platform BFF rewrites the prefix to /api/v2/hr on
 *     the backend. Same-origin BFF calls use Vercel's same-origin rewrite,
 *     avoiding cross-origin credentials — see lib/api/client.ts.)
 *   - Idempotency-Key header REQUIRED on every POST (mutation). The backend
 *     HrmIdempotentCommandExecutor returns 200-replay semantics: same key
 *     replays the original result; different keys produce independent
 *     attempts. See HrRecruitmentIdempotencyApiContractTest.
 *   - If-Match header on optimistic-locking endpoints is transported as
 *     X-SNAD-If-Match (Vercel edge rewrites standard If-Match → 412 — see
 *     lib/api/client.ts). The client wraps optimistic-lock calls with the
 *     custom transport header.
 *   - Error model: HrApiErrorCode + HrApiErrorResponse (G1-T10 design §15).
 *     Stable, deterministic, i18n-mappable codes — no PII echo, no stack
 *     traces. Errors are surfaced through parseHrmV2Error() so screens
 *     can map to user-facing Arabic strings via hr-labels.
 *
 * Privacy:
 *   - Candidate contact (email/phone) is masked by default; unmasked
 *     reads require HRM.PII.VIEW and are audit-logged server-side (§10).
 *     This client never persists PII in client cache beyond the
 *     permissioned DTO returned by the backend.
 */

import { apiClient } from "./client";
import { parseHrmV2Error, type HrmV2ApiError } from "./hr-v2-api";

export type UUID = string;
export type Instant = string;
export type LocalDate = string;

const RECRUITMENT_BASE = "/api/platform/api/v2/hr/recruitment";
const ONBOARDING_BASE = "/api/platform/api/v2/hr/onboarding";

type ReqOptions = { signal?: AbortSignal; timeoutMs?: number };

function headersWithIdempotency(idempotencyKey: string): Record<string, string> {
  return { "Idempotency-Key": idempotencyKey };
}

function buildRecruitment(path: string): string {
  return `${RECRUITMENT_BASE}${path}`;
}

function buildOnboarding(path: string): string {
  return `${ONBOARDING_BASE}${path}`;
}

// ---------------------------------------------------------------------------
// DTOs — recruitment (openings, candidates, applications, interviews, offers)
// ---------------------------------------------------------------------------

export interface IdResponse { id: UUID; }

export interface OpeningSummaryResponse {
  openingId: UUID;
  title: string;
  departmentName: string | null;
  headcount: number;
  filledCount: number;
  status: string; // DRAFT | IN_APPROVAL | OPEN | PAUSED | CLOSED | CANCELLED
  createdAt: Instant;
  updatedAt: Instant;
}

export interface OpeningDetailResponse {
  openingId: UUID;
  title: string;
  description: string | null;
  departmentId: UUID | null;
  departmentName: string | null;
  headcount: number;
  filledCount: number;
  status: string;
  complianceDecision: string | null; // APPROVED | PENDING | REJECTED
  stateHistory: OpeningStateEventResponse[];
  version: number;
  createdAt: Instant;
  updatedAt: Instant;
}

export interface OpeningStateEventResponse {
  fromStatus: string | null;
  toStatus: string;
  actor: string | null;
  reason: string | null;
  occurredAt: Instant;
}

export interface CreateOpeningRequest {
  title: string;
  description?: string;
  departmentId?: UUID;
  headcount: number;
}

export interface CandidateSummaryResponse {
  candidateId: UUID;
  displayName: string; // masked per §10 (e.g. "محمد ا.")
  poolState: string; // ACTIVE | DORMANT | ARCHIVED
  duplicateWarning: boolean;
  createdAt: Instant;
}

export interface CandidateDetailResponse {
  candidateId: UUID;
  displayName: string; // masked
  emailMasked: string; // m•••@example.com
  phoneMasked: string | null;
  poolState: string;
  duplicateWarning: boolean;
  applications: ApplicationSummaryResponse[];
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}

export interface CreateCandidateRequest {
  fullName: string;
  email: string;
  phone?: string;
}

export interface ApplicationSummaryResponse {
  applicationId: UUID;
  openingId: UUID;
  openingTitle: string;
  stage: string; // SUBMITTED | SCREENING | INTERVIEW | OFFERED | HIRED | REJECTED | WITHDRAWN
  state: string; // ACTIVE | ARCHIVED
  createdAt: Instant;
  updatedAt: Instant;
}

export interface ApplicationDetailResponse {
  applicationId: UUID;
  openingId: UUID;
  openingTitle: string;
  candidateId: UUID;
  candidateDisplayName: string;
  stage: string;
  state: string;
  stageHistory: ApplicationStageEventResponse[];
  interviews: InterviewSummaryResponse[];
  offers: OfferSummaryResponse[];
  version: number;
  createdAt: Instant;
  updatedAt: Instant;
}

export interface ApplicationStageEventResponse {
  fromStage: string | null;
  toStage: string;
  actor: string | null;
  reason: string | null;
  occurredAt: Instant;
}

export interface CreateApplicationRequest {
  openingId: UUID;
  candidateId: UUID;
  source?: string;
}

export interface PipelineStageColumnResponse {
  stage: string;
  applications: ApplicationSummaryResponse[];
}

export interface InterviewSummaryResponse {
  interviewId: UUID;
  applicationId: UUID;
  mode: string; // IN_PERSON | VIDEO | PHONE
  plannedAt: Instant;
  participantNames: string[];
  status: string; // SCHEDULED | COMPLETED | CANCELLED | NO_SHOW
  outcome: string | null;
}

export interface ScheduleInterviewRequest {
  mode: string;
  plannedAt: Instant;
  participantIds: UUID[];
  notes?: string;
}

export interface InterviewFeedbackResponse {
  interviewId: UUID;
  participantId: UUID;
  scorecard: Record<string, unknown>;
  submittedAt: Instant;
  submittedBy: UUID;
}

export interface PutInterviewFeedbackRequest {
  scorecard: Record<string, unknown>;
}

export interface OfferSummaryResponse {
  offerId: UUID;
  applicationId: UUID;
  version: number;
  status: string; // DRAFT | EXTENDED | ACCEPTED | DECLINED | WITHDRAWN | EXPIRED
  extendedAt: Instant | null;
  expiresAt: LocalDate | null;
}

export interface OfferDetailResponse {
  offerId: UUID;
  applicationId: UUID;
  version: number;
  status: string;
  payload: Record<string, unknown>; // versioned payload editor source
  extendedAt: Instant | null;
  expiresAt: LocalDate | null;
  approvalState: string | null; // PENDING | APPROVED | REJECTED
  createdAt: Instant;
  updatedAt: Instant;
}

export interface CreateOfferRequest {
  payload: Record<string, unknown>;
  expiresAt?: LocalDate;
}

// ---------------------------------------------------------------------------
// DTOs — onboarding (plans, tasks)
// ---------------------------------------------------------------------------

export interface OnboardingPlanSummaryResponse {
  planId: UUID;
  candidateDisplayName: string;
  employmentId: UUID | null;
  state: string; // PENDING | IN_PROGRESS | COMPLETED | CANCELLED
  tasksTotal: number;
  tasksCompleted: number;
  tasksOverdue: number;
  assigneeId: UUID | null;
  dueAt: LocalDate | null;
  createdAt: Instant;
  updatedAt: Instant;
}

export interface OnboardingPlanDetailResponse {
  planId: UUID;
  candidateDisplayName: string;
  employmentId: UUID | null;
  state: string;
  templateName: string | null;
  tasks: OnboardingTaskResponse[];
  assigneeId: UUID | null;
  dueAt: LocalDate | null;
  version: number;
  createdAt: Instant;
  updatedAt: Instant;
}

export interface OnboardingTaskResponse {
  taskId: UUID;
  title: string;
  description: string | null;
  state: string; // PENDING | COMPLETED | WAIVED | CANCELLED
  assigneeId: UUID | null;
  dueAt: LocalDate | null;
  completedAt: Instant | null;
  waiverReason: string | null;
  auditTrail: OnboardingTaskAuditEvent[];
}

export interface OnboardingTaskAuditEvent {
  action: string; // COMPLETE | WAIVE | RESET
  actor: string | null;
  reason: string | null;
  occurredAt: Instant;
}

export interface CreateOnboardingPlanRequest {
  candidateId: UUID;
  templateId?: UUID;
  assigneeId?: UUID;
  dueAt?: LocalDate;
}

export interface WaiveTaskRequest {
  reason: string;
}

// ---------------------------------------------------------------------------
// Client
// ---------------------------------------------------------------------------

export const hrmRecruitmentApi = {
  // ==================== Openings (8) ====================

  listOpenings(params?: { status?: string }, options?: ReqOptions) {
    return apiClient.request<OpeningSummaryResponse[]>({
      method: "GET",
      path: buildRecruitment("/openings"),
      query: { ...(params?.status ? { status: params.status } : {}) },
      ...options,
    });
  },

  getOpening(openingId: UUID, options?: ReqOptions) {
    return apiClient.request<OpeningDetailResponse>({
      method: "GET",
      path: buildRecruitment(`/openings/${openingId}`),
      ...options,
    });
  },

  createOpening(request: CreateOpeningRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<IdResponse, CreateOpeningRequest>({
      method: "POST",
      path: buildRecruitment("/openings"),
      body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  submitOpening(openingId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<void>({
      method: "POST",
      path: buildRecruitment(`/openings/${openingId}/submit`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  approveOpening(openingId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<void>({
      method: "POST",
      path: buildRecruitment(`/openings/${openingId}/approve`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  rejectOpening(openingId: UUID, idempotencyKey: string, reason: string, options?: ReqOptions) {
    return apiClient.request<void, { reason: string }>({
      method: "POST",
      path: buildRecruitment(`/openings/${openingId}/reject`),
      body: { reason },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  pauseOpening(openingId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<void>({
      method: "POST",
      path: buildRecruitment(`/openings/${openingId}/pause`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  closeOpening(openingId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<void>({
      method: "POST",
      path: buildRecruitment(`/openings/${openingId}/close`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  // ==================== Candidates (4) ====================

  listCandidates(params?: { poolState?: string }, options?: ReqOptions) {
    return apiClient.request<CandidateSummaryResponse[]>({
      method: "GET",
      path: buildRecruitment("/candidates"),
      query: { ...(params?.poolState ? { poolState: params.poolState } : {}) },
      ...options,
    });
  },

  getCandidate(candidateId: UUID, options?: ReqOptions) {
    return apiClient.request<CandidateDetailResponse>({
      method: "GET",
      path: buildRecruitment(`/candidates/${candidateId}`),
      ...options,
    });
  },

  createCandidate(request: CreateCandidateRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<IdResponse, CreateCandidateRequest>({
      method: "POST",
      path: buildRecruitment("/candidates"),
      body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  archiveCandidate(candidateId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<void>({
      method: "POST",
      path: buildRecruitment(`/candidates/${candidateId}/archive`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  // ==================== Applications (8) ====================

  listApplications(params?: { openingId?: UUID; stage?: string }, options?: ReqOptions) {
    return apiClient.request<ApplicationSummaryResponse[]>({
      method: "GET",
      path: buildRecruitment("/applications"),
      query: {
        ...(params?.openingId ? { openingId: params.openingId } : {}),
        ...(params?.stage ? { stage: params.stage } : {}),
      },
      ...options,
    });
  },

  getApplication(applicationId: UUID, options?: ReqOptions) {
    return apiClient.request<ApplicationDetailResponse>({
      method: "GET",
      path: buildRecruitment(`/applications/${applicationId}`),
      ...options,
    });
  },

  submitApplication(request: CreateApplicationRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<IdResponse, CreateApplicationRequest>({
      method: "POST",
      path: buildRecruitment("/applications"),
      body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  advanceApplication(applicationId: UUID, idempotencyKey: string, reason: string, options?: ReqOptions) {
    return apiClient.request<void, { reason: string }>({
      method: "POST",
      path: buildRecruitment(`/applications/${applicationId}/advance`),
      body: { reason },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  rejectApplication(applicationId: UUID, idempotencyKey: string, reason: string, options?: ReqOptions) {
    return apiClient.request<void, { reason: string }>({
      method: "POST",
      path: buildRecruitment(`/applications/${applicationId}/reject`),
      body: { reason },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  withdrawApplication(applicationId: UUID, idempotencyKey: string, reason: string, options?: ReqOptions) {
    return apiClient.request<void, { reason: string }>({
      method: "POST",
      path: buildRecruitment(`/applications/${applicationId}/withdraw`),
      body: { reason },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  getApplicationPipeline(applicationId: UUID, options?: ReqOptions) {
    return apiClient.request<PipelineStageColumnResponse[]>({
      method: "GET",
      path: buildRecruitment(`/applications/${applicationId}/pipeline`),
      ...options,
    });
  },

  // ==================== Interviews (5) ====================

  scheduleInterview(applicationId: UUID, request: ScheduleInterviewRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<IdResponse, ScheduleInterviewRequest>({
      method: "POST",
      path: buildRecruitment(`/applications/${applicationId}/interviews`),
      body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  getInterview(interviewId: UUID, options?: ReqOptions) {
    return apiClient.request<InterviewSummaryResponse>({
      method: "GET",
      path: buildRecruitment(`/interviews/${interviewId}`),
      ...options,
    });
  },

  recordInterviewOutcome(interviewId: UUID, outcome: string, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<void, { outcome: string }>({
      method: "POST",
      path: buildRecruitment(`/interviews/${interviewId}/outcome`),
      body: { outcome },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  putInterviewFeedback(interviewId: UUID, request: PutInterviewFeedbackRequest, options?: ReqOptions) {
    return apiClient.request<InterviewFeedbackResponse, PutInterviewFeedbackRequest>({
      method: "PUT",
      path: buildRecruitment(`/interviews/${interviewId}/feedback`),
      body: request,
      ...options,
    });
  },

  getInterviewFeedback(interviewId: UUID, options?: ReqOptions) {
    return apiClient.request<InterviewFeedbackResponse>({
      method: "GET",
      path: buildRecruitment(`/interviews/${interviewId}/feedback`),
      ...options,
    });
  },

  // ==================== Offers (6) ====================

  createOffer(applicationId: UUID, request: CreateOfferRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<IdResponse, CreateOfferRequest>({
      method: "POST",
      path: buildRecruitment(`/applications/${applicationId}/offers`),
      body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  extendOffer(offerId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<void>({
      method: "POST",
      path: buildRecruitment(`/offers/${offerId}/extend`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  acceptOffer(offerId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<void>({
      method: "POST",
      path: buildRecruitment(`/offers/${offerId}/accept`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  declineOffer(offerId: UUID, idempotencyKey: string, reason: string, options?: ReqOptions) {
    return apiClient.request<void, { reason: string }>({
      method: "POST",
      path: buildRecruitment(`/offers/${offerId}/decline`),
      body: { reason },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  withdrawOffer(offerId: UUID, idempotencyKey: string, reason: string, options?: ReqOptions) {
    return apiClient.request<void, { reason: string }>({
      method: "POST",
      path: buildRecruitment(`/offers/${offerId}/withdraw`),
      body: { reason },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  /** Hire conversion — the atomic, idempotent boundary (§7). */
  convertOfferToHire(offerId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<{ employmentId: UUID; planId: UUID }>({
      method: "POST",
      path: buildRecruitment(`/offers/${offerId}/hire-conversion`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },
};

export const hrmOnboardingApi = {
  // ==================== Onboarding Plans (4) ====================

  listPlans(params?: { state?: string; assigneeId?: UUID }, options?: ReqOptions) {
    return apiClient.request<OnboardingPlanSummaryResponse[]>({
      method: "GET",
      path: buildOnboarding("/plans"),
      query: {
        ...(params?.state ? { state: params.state } : {}),
        ...(params?.assigneeId ? { assigneeId: params.assigneeId } : {}),
      },
      ...options,
    });
  },

  getPlan(planId: UUID, options?: ReqOptions) {
    return apiClient.request<OnboardingPlanDetailResponse>({
      method: "GET",
      path: buildOnboarding(`/plans/${planId}`),
      ...options,
    });
  },

  createPlan(request: CreateOnboardingPlanRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<IdResponse, CreateOnboardingPlanRequest>({
      method: "POST",
      path: buildOnboarding("/plans"),
      body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  cancelPlan(planId: UUID, idempotencyKey: string, reason: string, options?: ReqOptions) {
    return apiClient.request<void, { reason: string }>({
      method: "POST",
      path: buildOnboarding(`/plans/${planId}/cancel`),
      body: { reason },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  // ==================== Onboarding Tasks (2) ====================

  completeTask(planId: UUID, taskId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<void>({
      method: "POST",
      path: buildOnboarding(`/plans/${planId}/tasks/${taskId}/complete`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  waiveTask(planId: UUID, taskId: UUID, request: WaiveTaskRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<void, WaiveTaskRequest>({
      method: "POST",
      path: buildOnboarding(`/plans/${planId}/tasks/${taskId}/waive`),
      body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },
};

// Re-export parseHrmV2Error for screens using this module.
export { parseHrmV2Error, type HrmV2ApiError };
