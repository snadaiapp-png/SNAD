/**
 * HR v2 Recruitment & Onboarding API client (G1-T11).
 * ---------------------------------------------------------------------------
 * Source of truth: apps/sanad-platform/src/main/java/com/sanad/platform/hr/api/v2/
 *   - recruitment/HrRecruitmentV2Controller.java
 *   - recruitment/HrRecruitmentApiQueryService.java
 *   - onboarding/HrOnboardingV2Controller.java
 *   - onboarding/HrOnboardingApiQueryService.java
 *
 * The backend list contract is CursorPage<T> = { items, nextCursor }.  The UI
 * predates that shape and consumes arrays, so this module is the single
 * compatibility boundary: it follows cursors, maps canonical backend field
 * names to the UI view-model names, and translates legacy UI command payloads
 * to the canonical controller request records.  Keeping the normalization here
 * prevents React pages from re-implementing transport details and prevents the
 * production failure where pages treated CursorPage objects as arrays.
 */

import { apiClient } from "./client";
import { parseHrmV2Error, type HrmV2ApiError } from "./hr-v2-api";

export type UUID = string;
export type Instant = string;
export type LocalDate = string;

const RECRUITMENT_BASE = "/api/platform/api/v2/hr/recruitment";
const ONBOARDING_BASE = "/api/platform/api/v2/hr/onboarding";
const MAX_PAGE_SIZE = 100;

type ReqOptions = { signal?: AbortSignal; timeoutMs?: number };

interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
}

interface BackendOpeningView {
  id: UUID;
  openingNumber: string;
  jobId: UUID;
  jobVersionId: UUID | null;
  orgUnitId: UUID;
  positionId: UUID | null;
  state: string;
  requestedHeadcount: number;
  filledHeadcount: number;
  complianceDecision: string | null;
  complianceDecidedAt: Instant | null;
  opensAt: Instant | null;
  closesAt: Instant | null;
  version: number;
}

interface BackendCandidateSummary {
  id: UUID;
  candidateNumber: string;
  displayName: string;
  poolState: string;
  version: number;
}

interface BackendCandidateView {
  id: UUID;
  candidateNumber: string;
  displayName: string;
  poolState: string;
  email: string | null;
  phone: string | null;
}

interface BackendApplicationView {
  id: UUID;
  candidateId: UUID;
  jobOpeningId: UUID;
  state: string;
  appliedAt: Instant | null;
  version: number;
}

interface BackendStagePeriod {
  id: UUID;
  tenantId: UUID;
  applicationId: UUID;
  stage: string;
  fromAt: Instant;
}

interface BackendInterviewRow {
  id: UUID;
  tenantId: UUID;
  applicationId: UUID;
  plannedAt: Instant;
  mode: string;
  durationMinutes: number | null;
  state: string;
  outcome: string | null;
  version: number;
}

interface BackendFeedbackView {
  id: UUID;
  participantUserId: UUID;
  scorecard: Record<string, unknown>;
  outcome: string | null;
  version: number;
  updatedAt: Instant;
}

interface BackendPlanSummary {
  id: UUID;
  planNumber: string;
  employmentId: UUID;
  state: string;
  workflowLinked: boolean;
  version: number;
  createdAt: Instant;
  updatedAt: Instant;
}

interface BackendTaskView {
  id: UUID;
  sequence: number;
  title: string;
  state: string;
  assigneeUserId: UUID | null;
  dueAt: Instant | null;
  reasonCode: string | null;
  resolvedAt: Instant | null;
}

interface BackendPlanDetail {
  plan: BackendPlanSummary;
  tasks: BackendTaskView[];
}

function headersWithIdempotency(idempotencyKey: string): Record<string, string> {
  return { "Idempotency-Key": idempotencyKey };
}

function buildRecruitment(path: string): string {
  return `${RECRUITMENT_BASE}${path}`;
}

function buildOnboarding(path: string): string {
  return `${ONBOARDING_BASE}${path}`;
}

function dateOnly(value: string | null | undefined): string | null {
  return value ? value.slice(0, 10) : null;
}

async function fetchAllCursorPages<T>(path: string, options?: ReqOptions): Promise<T[]> {
  const rows: T[] = [];
  const seen = new Set<string>();
  let cursor: string | null = null;

  do {
    const response = await apiClient.request<CursorPage<T> | T[]>({
      method: "GET",
      path,
      query: {
        limit: MAX_PAGE_SIZE,
        ...(cursor ? { cursor } : {}),
      },
      ...options,
    });

    // Compatibility for a short-lived pre-cursor backend build. Production and
    // the canonical Java controllers return CursorPage<T>.
    if (Array.isArray(response)) {
      rows.push(...response);
      break;
    }

    rows.push(...(response?.items ?? []));
    const next = response?.nextCursor ?? null;
    if (next && seen.has(next)) {
      throw new Error("HRM cursor pagination returned a repeated cursor");
    }
    if (next) seen.add(next);
    cursor = next;
  } while (cursor);

  return rows;
}

function mapOpening(raw: BackendOpeningView): OpeningSummaryResponse {
  const activityDate = raw.complianceDecidedAt ?? raw.opensAt ?? "";
  return {
    openingId: raw.id,
    // The canonical query service exposes openingNumber, not a denormalized job
    // title.  Use the real identifier instead of inventing a title.
    title: raw.openingNumber,
    // The current UI column is a compatibility slot.  The only authoritative
    // value exposed by this endpoint is orgUnitId, so surface that real value.
    departmentName: raw.orgUnitId,
    headcount: raw.requestedHeadcount,
    filledCount: raw.filledHeadcount,
    status: raw.state,
    createdAt: dateOnly(raw.opensAt) ?? "",
    updatedAt: dateOnly(activityDate) ?? "",
    openingNumber: raw.openingNumber,
    jobId: raw.jobId,
    jobVersionId: raw.jobVersionId,
    orgUnitId: raw.orgUnitId,
    positionId: raw.positionId,
    complianceDecision: raw.complianceDecision,
    version: raw.version,
  };
}

function mapCandidate(raw: BackendCandidateSummary): CandidateSummaryResponse {
  return {
    candidateId: raw.id,
    displayName: raw.displayName,
    poolState: raw.poolState,
    // The canonical list projection does not expose duplicate-warning state or
    // timestamps.  Null/blank explicitly means "not provided by this view".
    duplicateWarning: null,
    createdAt: "",
    candidateNumber: raw.candidateNumber,
    version: raw.version,
  };
}

function mapApplication(raw: BackendApplicationView): ApplicationSummaryResponse {
  return {
    applicationId: raw.id,
    openingId: raw.jobOpeningId,
    openingTitle: raw.jobOpeningId,
    candidateId: raw.candidateId,
    stage: raw.state,
    state: raw.state,
    createdAt: raw.appliedAt ?? "",
    updatedAt: raw.appliedAt ?? "",
    version: raw.version,
  };
}

function mapTask(raw: BackendTaskView): OnboardingTaskResponse {
  return {
    taskId: raw.id,
    title: raw.title,
    description: null,
    state: raw.state === "DONE" ? "COMPLETED" : raw.state,
    assigneeId: raw.assigneeUserId,
    dueAt: dateOnly(raw.dueAt),
    completedAt: raw.resolvedAt,
    waiverReason: raw.reasonCode,
    auditTrail: [],
  };
}

function taskIsResolved(task: BackendTaskView): boolean {
  return task.state === "DONE" || task.state === "COMPLETED" || task.state === "WAIVED";
}

function taskIsOverdue(task: BackendTaskView): boolean {
  const due = dateOnly(task.dueAt);
  if (!due || taskIsResolved(task) || task.state === "CANCELLED") return false;
  return due < new Date().toISOString().slice(0, 10);
}

function mapPlanDetail(raw: BackendPlanDetail): OnboardingPlanDetailResponse {
  const tasks = raw.tasks.map(mapTask);
  const assigneeId = raw.tasks.find((task) => task.assigneeUserId)?.assigneeUserId ?? null;
  const dueDates = raw.tasks.map((task) => dateOnly(task.dueAt)).filter((value): value is string => Boolean(value));
  return {
    planId: raw.plan.id,
    // The backend does not expose candidate PII from onboarding reads.  The
    // plan number is the authoritative non-PII display identifier.
    candidateDisplayName: raw.plan.planNumber,
    employmentId: raw.plan.employmentId,
    state: raw.plan.state,
    templateName: null,
    tasks,
    assigneeId,
    dueAt: dueDates.sort()[0] ?? null,
    version: raw.plan.version,
    createdAt: raw.plan.createdAt,
    updatedAt: raw.plan.updatedAt,
    planNumber: raw.plan.planNumber,
    workflowLinked: raw.plan.workflowLinked,
  };
}

function mapPlanSummary(raw: BackendPlanDetail): OnboardingPlanSummaryResponse {
  const detail = mapPlanDetail(raw);
  return {
    planId: detail.planId,
    candidateDisplayName: detail.candidateDisplayName,
    employmentId: detail.employmentId,
    state: detail.state,
    tasksTotal: raw.tasks.length,
    tasksCompleted: raw.tasks.filter(taskIsResolved).length,
    tasksOverdue: raw.tasks.filter(taskIsOverdue).length,
    assigneeId: detail.assigneeId,
    dueAt: detail.dueAt,
    createdAt: detail.createdAt,
    updatedAt: detail.updatedAt,
    planNumber: raw.plan.planNumber,
    workflowLinked: raw.plan.workflowLinked,
    version: raw.plan.version,
  };
}

// ---------------------------------------------------------------------------
// DTOs — recruitment (UI view models)
// ---------------------------------------------------------------------------

export interface IdResponse { id: UUID; }

export interface OpeningSummaryResponse {
  openingId: UUID;
  title: string;
  departmentName: string | null;
  headcount: number;
  filledCount: number;
  status: string;
  createdAt: Instant;
  updatedAt: Instant;
  openingNumber?: string;
  jobId?: UUID;
  jobVersionId?: UUID | null;
  orgUnitId?: UUID;
  positionId?: UUID | null;
  complianceDecision?: string | null;
  version?: number;
}

export interface OpeningDetailResponse extends OpeningSummaryResponse {
  description: string | null;
  departmentId: UUID | null;
  stateHistory: OpeningStateEventResponse[];
  version: number;
}

export interface OpeningStateEventResponse {
  fromStatus: string | null;
  toStatus: string;
  actor: string | null;
  reason: string | null;
  occurredAt: Instant;
}

/** Legacy web create shape retained for compatibility; canonical fields may be supplied directly. */
export interface CreateOpeningRequest {
  title?: string;
  description?: string;
  departmentId?: UUID;
  headcount?: number;
  jobId?: UUID;
  jobVersionId?: UUID;
  orgUnitId?: UUID;
  positionId?: UUID;
  requestedHeadcount?: number;
  opensAt?: Instant;
  closesAt?: Instant;
}

export interface CandidateSummaryResponse {
  candidateId: UUID;
  displayName: string;
  poolState: string;
  duplicateWarning: boolean | null;
  createdAt: Instant;
  candidateNumber?: string;
  version?: number;
}

export interface CandidateDetailResponse {
  candidateId: UUID;
  displayName: string;
  emailMasked: string | null;
  phoneMasked: string | null;
  poolState: string;
  duplicateWarning: boolean | null;
  applications: ApplicationSummaryResponse[];
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
  candidateNumber?: string;
}

export interface CreateCandidateRequest {
  fullName?: string;
  displayName?: string;
  email?: string;
  phone?: string;
  compensationExpectations?: string;
  iamUserId?: UUID;
}

export interface ApplicationSummaryResponse {
  applicationId: UUID;
  openingId: UUID;
  openingTitle: string;
  candidateId?: UUID;
  stage: string;
  state: string;
  createdAt: Instant;
  updatedAt: Instant;
  version?: number;
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
  mode: string;
  plannedAt: Instant;
  participantNames: string[];
  status: string;
  outcome: string | null;
  version?: number;
}

export interface ScheduleInterviewRequest {
  mode: string;
  plannedAt: Instant;
  participantIds: UUID[];
  notes?: string;
  durationMinutes?: number;
}

export interface InterviewFeedbackResponse {
  interviewId: UUID;
  participantId: UUID;
  scorecard: Record<string, unknown>;
  submittedAt: Instant;
  submittedBy: UUID;
  version: number;
  outcome?: string | null;
}

export interface PutInterviewFeedbackRequest {
  scorecard: Record<string, unknown>;
}

export interface OfferSummaryResponse {
  offerId: UUID;
  applicationId: UUID;
  version: number;
  status: string;
  extendedAt: Instant | null;
  expiresAt: LocalDate | null;
}

export interface OfferDetailResponse {
  offerId: UUID;
  applicationId: UUID;
  version: number;
  status: string;
  payload: Record<string, unknown>;
  extendedAt: Instant | null;
  expiresAt: LocalDate | null;
  approvalState: string | null;
  createdAt: Instant;
  updatedAt: Instant;
}

export interface CreateOfferRequest {
  payload?: Record<string, unknown>;
  contractTerms?: Record<string, unknown>;
  compensation?: Record<string, unknown>;
  expiresAt?: LocalDate | Instant;
}

export interface HireConversionRequest {
  identityClaims?: Array<{
    identifierType: string;
    issuingCountryCode?: string;
    value: string;
  }>;
  legalEntityId: UUID;
  workerClassificationCode: string;
  laborJurisdictionCode?: string;
  employmentStartDate: LocalDate;
  allocationPercent?: number;
  positionId?: UUID;
  contractNumber?: string;
}

export interface HireConversionResponse {
  offerId?: UUID;
  applicationId?: UUID;
  personId?: UUID;
  personReused?: boolean;
  employeeNumber?: string;
  employmentId: UUID;
  assignmentId?: UUID;
  contractId?: UUID;
  compensationPackageId?: UUID;
  onboardingPlanId: UUID;
  replayed?: boolean;
  /** Legacy UI alias. */
  planId: UUID;
}

// ---------------------------------------------------------------------------
// DTOs — onboarding (UI view models)
// ---------------------------------------------------------------------------

export interface OnboardingPlanSummaryResponse {
  planId: UUID;
  candidateDisplayName: string;
  employmentId: UUID | null;
  state: string;
  tasksTotal: number;
  tasksCompleted: number;
  tasksOverdue: number;
  assigneeId: UUID | null;
  dueAt: LocalDate | null;
  createdAt: Instant;
  updatedAt: Instant;
  planNumber?: string;
  workflowLinked?: boolean;
  version?: number;
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
  planNumber?: string;
  workflowLinked?: boolean;
}

export interface OnboardingTaskResponse {
  taskId: UUID;
  title: string;
  description: string | null;
  state: string;
  assigneeId: UUID | null;
  dueAt: LocalDate | null;
  completedAt: Instant | null;
  waiverReason: string | null;
  auditTrail: OnboardingTaskAuditEvent[];
}

export interface OnboardingTaskAuditEvent {
  action: string;
  actor: string | null;
  reason: string | null;
  occurredAt: Instant;
}

export interface CreateOnboardingPlanRequest {
  candidateId?: UUID;
  templateId?: UUID;
  assigneeId?: UUID;
  dueAt?: LocalDate;
  employmentId?: UUID;
  templateCode?: string;
  workflowLinked?: boolean;
}

export interface WaiveTaskRequest {
  reason: string;
}

// ---------------------------------------------------------------------------
// Client
// ---------------------------------------------------------------------------

export const hrmRecruitmentApi = {
  // ==================== Openings ====================

  async listOpenings(params?: { status?: string }, options?: ReqOptions): Promise<OpeningSummaryResponse[]> {
    const raw = await fetchAllCursorPages<BackendOpeningView>(buildRecruitment("/openings"), options);
    const mapped = raw.map(mapOpening);
    return params?.status ? mapped.filter((row) => row.status === params.status) : mapped;
  },

  async getOpening(openingId: UUID, options?: ReqOptions): Promise<OpeningDetailResponse> {
    const raw = await apiClient.request<BackendOpeningView>({
      method: "GET",
      path: buildRecruitment(`/openings/${openingId}`),
      ...options,
    });
    return {
      ...mapOpening(raw),
      description: null,
      departmentId: raw.orgUnitId,
      stateHistory: [],
      version: raw.version,
    };
  },

  createOpening(request: CreateOpeningRequest, idempotencyKey: string, options?: ReqOptions) {
    const jobId = request.jobId;
    const orgUnitId = request.orgUnitId ?? request.departmentId;
    const requestedHeadcount = request.requestedHeadcount ?? request.headcount;
    return apiClient.request<IdResponse, Record<string, unknown>>({
      method: "POST",
      path: buildRecruitment("/openings"),
      body: {
        jobId,
        jobVersionId: request.jobVersionId,
        orgUnitId,
        positionId: request.positionId,
        requestedHeadcount,
        opensAt: request.opensAt,
        closesAt: request.closesAt,
      },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  submitOpening(openingId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<{ id: UUID; workflowInstanceId: UUID }>({
      method: "POST",
      path: buildRecruitment(`/openings/${openingId}/submit`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  approveOpening(openingId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<{ id: UUID; state: string }>({
      method: "POST",
      path: buildRecruitment(`/openings/${openingId}/approve`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  rejectOpening(openingId: UUID, idempotencyKey: string, reason: string, options?: ReqOptions) {
    return apiClient.request<{ id: UUID; state: string }, { reasonCode: string }>({
      method: "POST",
      path: buildRecruitment(`/openings/${openingId}/reject`),
      body: { reasonCode: reason },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  pauseOpening(openingId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<{ id: UUID; state: string }>({
      method: "POST",
      path: buildRecruitment(`/openings/${openingId}/pause`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  resumeOpening(openingId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<{ id: UUID; state: string }>({
      method: "POST",
      path: buildRecruitment(`/openings/${openingId}/resume`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  closeOpening(openingId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<{ id: UUID; state: string }>({
      method: "POST",
      path: buildRecruitment(`/openings/${openingId}/close`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  // ==================== Candidates ====================

  async listCandidates(params?: { poolState?: string }, options?: ReqOptions): Promise<CandidateSummaryResponse[]> {
    const raw = await fetchAllCursorPages<BackendCandidateSummary>(buildRecruitment("/candidates"), options);
    const mapped = raw.map(mapCandidate);
    return params?.poolState ? mapped.filter((row) => row.poolState === params.poolState) : mapped;
  },

  async getCandidate(candidateId: UUID, options?: ReqOptions): Promise<CandidateDetailResponse> {
    const raw = await apiClient.request<BackendCandidateView>({
      method: "GET",
      path: buildRecruitment(`/candidates/${candidateId}`),
      ...options,
    });
    return {
      candidateId: raw.id,
      candidateNumber: raw.candidateNumber,
      displayName: raw.displayName,
      emailMasked: raw.email,
      phoneMasked: raw.phone,
      poolState: raw.poolState,
      duplicateWarning: null,
      applications: [],
      createdAt: "",
      updatedAt: "",
      version: 0,
    };
  },

  createCandidate(request: CreateCandidateRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<IdResponse, Record<string, unknown>>({
      method: "POST",
      path: buildRecruitment("/candidates"),
      body: {
        displayName: request.displayName ?? request.fullName,
        email: request.email,
        phone: request.phone,
        compensationExpectations: request.compensationExpectations,
        iamUserId: request.iamUserId,
      },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  archiveCandidate(candidateId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<{ id: UUID; state: string }>({
      method: "POST",
      path: buildRecruitment(`/candidates/${candidateId}/archive`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  // ==================== Applications ====================

  async listApplications(params?: { openingId?: UUID; stage?: string }, options?: ReqOptions): Promise<ApplicationSummaryResponse[]> {
    const raw = await fetchAllCursorPages<BackendApplicationView>(buildRecruitment("/applications"), options);
    let mapped = raw.map(mapApplication);
    if (params?.openingId) mapped = mapped.filter((row) => row.openingId === params.openingId);
    if (params?.stage) mapped = mapped.filter((row) => row.stage === params.stage);
    return mapped;
  },

  async getApplication(applicationId: UUID, options?: ReqOptions): Promise<ApplicationDetailResponse> {
    const [raw, periods] = await Promise.all([
      apiClient.request<BackendApplicationView>({
        method: "GET",
        path: buildRecruitment(`/applications/${applicationId}`),
        ...options,
      }),
      apiClient.request<BackendStagePeriod[]>({
        method: "GET",
        path: buildRecruitment(`/applications/${applicationId}/pipeline`),
        ...options,
      }),
    ]);
    const summary = mapApplication(raw);
    const ordered = [...periods].sort((a, b) => a.fromAt.localeCompare(b.fromAt));
    const stageHistory = ordered.map((period, index): ApplicationStageEventResponse => ({
      fromStage: index > 0 ? ordered[index - 1].stage : null,
      toStage: period.stage,
      actor: null,
      reason: null,
      occurredAt: period.fromAt,
    }));
    return {
      applicationId: summary.applicationId,
      openingId: summary.openingId,
      openingTitle: summary.openingTitle,
      candidateId: raw.candidateId,
      candidateDisplayName: raw.candidateId,
      stage: summary.stage,
      state: summary.state,
      stageHistory,
      interviews: [],
      offers: [],
      version: raw.version,
      createdAt: summary.createdAt,
      updatedAt: summary.updatedAt,
    };
  },

  submitApplication(request: CreateApplicationRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<IdResponse, { candidateId: UUID; jobOpeningId: UUID }>({
      method: "POST",
      path: buildRecruitment("/applications"),
      body: { candidateId: request.candidateId, jobOpeningId: request.openingId },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  advanceApplication(applicationId: UUID, idempotencyKey: string, _reason?: string, options?: ReqOptions) {
    return apiClient.request<{ id: UUID; state: string }>({
      method: "POST",
      path: buildRecruitment(`/applications/${applicationId}/advance`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  rejectApplication(applicationId: UUID, idempotencyKey: string, reason: string, options?: ReqOptions) {
    return apiClient.request<{ id: UUID; state: string }, { reasonCode: string }>({
      method: "POST",
      path: buildRecruitment(`/applications/${applicationId}/reject`),
      body: { reasonCode: reason },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  withdrawApplication(applicationId: UUID, idempotencyKey: string, reason: string, options?: ReqOptions) {
    return apiClient.request<{ id: UUID; state: string }, { reasonCode: string }>({
      method: "POST",
      path: buildRecruitment(`/applications/${applicationId}/withdraw`),
      body: { reasonCode: reason },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  getApplicationPipeline(applicationId: UUID, options?: ReqOptions) {
    return apiClient.request<BackendStagePeriod[]>({
      method: "GET",
      path: buildRecruitment(`/applications/${applicationId}/pipeline`),
      ...options,
    });
  },

  // ==================== Interviews ====================

  scheduleInterview(applicationId: UUID, request: ScheduleInterviewRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<IdResponse, Record<string, unknown>>({
      method: "POST",
      path: buildRecruitment(`/applications/${applicationId}/interviews`),
      body: {
        mode: request.mode,
        plannedAt: request.plannedAt,
        ...(request.durationMinutes !== undefined ? { durationMinutes: request.durationMinutes } : {}),
        panelUserIds: request.participantIds,
      },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  async getInterview(interviewId: UUID, options?: ReqOptions): Promise<InterviewSummaryResponse> {
    const raw = await apiClient.request<BackendInterviewRow>({
      method: "GET",
      path: buildRecruitment(`/interviews/${interviewId}`),
      ...options,
    });
    return {
      interviewId: raw.id,
      applicationId: raw.applicationId,
      mode: raw.mode,
      plannedAt: raw.plannedAt,
      participantNames: [],
      status: raw.state,
      outcome: raw.outcome,
      version: raw.version,
    };
  },

  recordInterviewOutcome(interviewId: UUID, outcome: string, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<{ id: UUID; state: string }, { state: string; outcome: string }>({
      method: "POST",
      path: buildRecruitment(`/interviews/${interviewId}/outcome`),
      body: { state: "DONE", outcome },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  putInterviewFeedback(
    interviewId: UUID,
    request: PutInterviewFeedbackRequest,
    idempotencyKey: string,
    version?: number,
    options?: ReqOptions,
  ) {
    const headers = headersWithIdempotency(idempotencyKey);
    if (version !== undefined) headers["If-Match"] = String(version);
    return apiClient.request<{ id: UUID; state: string }, PutInterviewFeedbackRequest>({
      method: "PUT",
      path: buildRecruitment(`/interviews/${interviewId}/feedback`),
      body: request,
      context: { headers },
      ...options,
    });
  },

  async getInterviewFeedback(interviewId: UUID, options?: ReqOptions): Promise<InterviewFeedbackResponse[]> {
    const rows = await apiClient.request<BackendFeedbackView[]>({
      method: "GET",
      path: buildRecruitment(`/interviews/${interviewId}/feedback`),
      ...options,
    });
    return rows.map((row) => ({
      interviewId,
      participantId: row.participantUserId,
      scorecard: row.scorecard,
      submittedAt: row.updatedAt,
      submittedBy: row.participantUserId,
      version: row.version,
      outcome: row.outcome,
    }));
  },

  // ==================== Offers ====================

  createOffer(applicationId: UUID, request: CreateOfferRequest, idempotencyKey: string, options?: ReqOptions) {
    const fallback = request.payload ?? {};
    return apiClient.request<IdResponse, Record<string, unknown>>({
      method: "POST",
      path: buildRecruitment(`/applications/${applicationId}/offers`),
      body: {
        contractTerms: request.contractTerms ?? fallback,
        compensation: request.compensation ?? fallback,
        expiresAt: request.expiresAt,
      },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  extendOffer(offerId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<{ id: UUID; workflowInstanceId: UUID }>({
      method: "POST",
      path: buildRecruitment(`/offers/${offerId}/extend`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  acceptOffer(offerId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<unknown>({
      method: "POST",
      path: buildRecruitment(`/offers/${offerId}/accept`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  declineOffer(offerId: UUID, idempotencyKey: string, _reason?: string, options?: ReqOptions) {
    return apiClient.request<unknown>({
      method: "POST",
      path: buildRecruitment(`/offers/${offerId}/decline`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  withdrawOffer(offerId: UUID, idempotencyKey: string, reason: string, options?: ReqOptions) {
    return apiClient.request<unknown, { reasonCode: string }>({
      method: "POST",
      path: buildRecruitment(`/offers/${offerId}/withdraw`),
      body: { reasonCode: reason },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  async convertOfferToHire(
    offerId: UUID,
    idempotencyKey: string,
    request?: HireConversionRequest,
    options?: ReqOptions,
  ): Promise<HireConversionResponse> {
    const raw = await apiClient.request<Record<string, unknown>, HireConversionRequest | Record<string, never>>({
      method: "POST",
      path: buildRecruitment(`/offers/${offerId}/hire-conversion`),
      body: request ?? {},
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
    const onboardingPlanId = String(raw.onboardingPlanId ?? raw.planId ?? "");
    return {
      ...(raw as unknown as Omit<HireConversionResponse, "planId" | "onboardingPlanId">),
      employmentId: String(raw.employmentId ?? ""),
      onboardingPlanId,
      planId: onboardingPlanId,
    };
  },
};

export const hrmOnboardingApi = {
  async listPlans(params?: { state?: string; assigneeId?: UUID }, options?: ReqOptions): Promise<OnboardingPlanSummaryResponse[]> {
    let summaries = await fetchAllCursorPages<BackendPlanSummary>(buildOnboarding("/plans"), options);
    if (params?.state) summaries = summaries.filter((plan) => plan.state === params.state);

    const details = await Promise.all(summaries.map((plan) =>
      apiClient.request<BackendPlanDetail>({
        method: "GET",
        path: buildOnboarding(`/plans/${plan.id}`),
        ...options,
      }),
    ));
    let mapped = details.map(mapPlanSummary);
    if (params?.assigneeId) mapped = mapped.filter((plan) => plan.assigneeId === params.assigneeId);
    return mapped;
  },

  async getPlan(planId: UUID, options?: ReqOptions): Promise<OnboardingPlanDetailResponse> {
    const raw = await apiClient.request<BackendPlanDetail>({
      method: "GET",
      path: buildOnboarding(`/plans/${planId}`),
      ...options,
    });
    return mapPlanDetail(raw);
  },

  createPlan(request: CreateOnboardingPlanRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<IdResponse, Record<string, unknown>>({
      method: "POST",
      path: buildOnboarding("/plans"),
      body: {
        employmentId: request.employmentId,
        templateCode: request.templateCode,
        workflowLinked: request.workflowLinked ?? false,
      },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  cancelPlan(planId: UUID, idempotencyKey: string, reason: string, options?: ReqOptions) {
    return apiClient.request<{ id: UUID; state: string }, { reasonCode: string }>({
      method: "POST",
      path: buildOnboarding(`/plans/${planId}/cancel`),
      body: { reasonCode: reason },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  completeTask(planId: UUID, taskId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<{ id: UUID; state: string }>({
      method: "POST",
      path: buildOnboarding(`/plans/${planId}/tasks/${taskId}/complete`),
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },

  waiveTask(planId: UUID, taskId: UUID, request: WaiveTaskRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<{ id: UUID; state: string }, { reasonCode: string }>({
      method: "POST",
      path: buildOnboarding(`/plans/${planId}/tasks/${taskId}/waive`),
      body: { reasonCode: request.reason },
      context: { headers: headersWithIdempotency(idempotencyKey) },
      ...options,
    });
  },
};

// Re-export parseHrmV2Error for screens using this module.
export { parseHrmV2Error, type HrmV2ApiError };
