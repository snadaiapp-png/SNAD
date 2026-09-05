/**
 * Typed web client for the canonical HRM v2 API (`/api/v2/hr`) — WS5 Task 8.
 *
 * Architecture rules (authoritative WS5 plan):
 * - Request and response types are declared separately and mirror the
 *   backend v2 DTO records exactly. `Partial<...>` of a response type is
 *   never used as a request type.
 * - The client consumes the canonical API through the existing `apiClient`
 *   (same-origin BFF base `/api/platform`). No direct DB access, no
 *   duplicated backend domain logic, no invented permissions or business
 *   rules.
 * - Every critical POST command sends an `Idempotency-Key` header; commands
 *   with optimistic concurrency send `expectedVersion` inside the body —
 *   exactly as the backend controllers require.
 * - The canonical v2 error envelope (`{code, message, violations[]}`) is
 *   parsed into a typed `HrmV2ApiError` (WS5 Task 2 model).
 * - Private PII is only fetched through the explicit audited private
 *   endpoints; directory/list surfaces never receive restricted fields.
 */

import { apiClient } from "./client";

const HRM_V2_BASE = "/api/platform/api/v2/hr";

// ---------------------------------------------------------------------------
// Primitive aliases — backend UUID/LocalDate/Instant serialize as strings.
// ---------------------------------------------------------------------------

/** Backend `UUID`. */
type UUID = string;
/** Backend `LocalDate` (`YYYY-MM-DD`). */
type LocalDate = string;
/** Backend `Instant` (ISO-8601). */
export type Instant = string;

// ---------------------------------------------------------------------------
// Response DTOs — mirror apps/.../hr/api/v2/dto/*Response.java
// ---------------------------------------------------------------------------

export interface PersonSummaryResponse {
  personId: UUID;
  userId: UUID | null;
  firstName: string;
  middleName: string | null;
  lastName: string;
  displayName: string;
  version: number;
}

export interface PersonPrivateResponse {
  personId: UUID;
  dateOfBirth: LocalDate | null;
  nationalityCountryCode: string | null;
  maritalStatus: string | null;
  version: number;
}

export interface PersonPrivateMutationResponse {
  personId: UUID;
  version: number;
}

export interface IdentifierMetadataResponse {
  identifierId: UUID;
  identifierType: string;
  issuingCountryCode: string | null;
  status: string;
}

export interface PersonLinkResponse {
  personId: UUID;
  userId: UUID;
  linked: boolean;
}

export interface EmploymentResponse {
  employmentId: UUID;
  personId: UUID;
  legalEntityId: UUID;
  employeeNumber: string;
  workerClassificationCode: string;
  currentStatus: string;
  employmentStartDate: LocalDate;
  terminationDate: LocalDate | null;
  rehireOfEmployeeId: UUID | null;
  version: number;
}

export type AssignmentType = "PRIMARY" | "SECONDARY" | "MATRIX" | string;
export type OccupancyMode = "DEDICATED" | "SHARED" | "PLACEHOLDER" | string;

export interface AssignmentResponse {
  assignmentId: UUID;
  employmentId: UUID;
  organizationId: UUID;
  orgUnitId: UUID | null;
  positionId: UUID | null;
  reportsToAssignmentId: UUID | null;
  assignmentType: AssignmentType;
  occupancyMode: OccupancyMode;
  allocationPercent: number | null;
  effectiveFrom: LocalDate;
  effectiveTo: LocalDate | null;
  status: string;
  version: number;
}

export interface OrgUnitResponse {
  orgUnitId: UUID;
  name: string;
  code: string;
  unitType: string;
  parentOrgUnitId: UUID | null;
  effectiveFrom: LocalDate;
  effectiveTo: LocalDate | null;
  status: string;
}

export interface JobResponse {
  jobId: UUID;
  title: string;
  description: string | null;
  grade: string | null;
  effectiveFrom: LocalDate;
  effectiveTo: LocalDate | null;
  status: string;
}

export interface PositionResponse {
  positionId: UUID;
  staffability: string;
  title: string;
  jobId: UUID | null;
  orgUnitId: UUID | null;
  effectiveFrom: LocalDate;
  effectiveTo: LocalDate | null;
  status: string;
}

export interface ContractResponse {
  contractId: UUID;
  employmentId: UUID;
  contractNumber: string;
  isPrimary: boolean;
  versionNumber: number | null;
  status: string;
  contractTermType: string | null;
  contractStartDate: LocalDate | null;
  contractEndDate: LocalDate | null;
  effectiveDate: LocalDate;
  documentReference: string | null;
  complianceStatus: string | null;
  packCode: string | null;
  packVersion: string | null;
}

export interface CompensationComponent {
  componentCode: string;
  componentType: string;
  amount: number | null;
  percentage: string | null;
}

export interface CompensationPackageResponse {
  packageId: UUID;
  employmentId: UUID;
  currencyCode: string;
  payFrequency: string | null;
  effectiveFrom: LocalDate;
  effectiveTo: LocalDate | null;
  status: string;
  version: number;
  components: CompensationComponent[];
}

export interface ComplianceContextResponse {
  laborJurisdiction: string;
  mode: string;
  packCode: string | null;
  packVersion: string | null;
  workerClassification: string;
  effectiveDate: LocalDate | null;
}

export interface OverrideRequestResponse {
  requestId: UUID;
  complianceRuleId: UUID;
  resourceType: string;
  resourceId: UUID;
  requesterUserId: UUID;
  justification: string;
  evidenceReference: string | null;
  approvedBy: UUID | null;
  approvalComment: string | null;
  validFrom: LocalDate | null;
  validUntil: LocalDate | null;
  status: string;
  executedAt: Instant | null;
}

export interface AuditEntryResponse {
  auditId: UUID;
  action: string;
  resourceType: string;
  resourceId: UUID;
  dataClassification: string;
  reason: string | null;
  result: string;
  actorUserId: UUID | null;
  occurredAt: Instant;
}

export interface LifecycleCommandResponse {
  employmentId: UUID;
  previousStatus: string;
  newStatus: string;
  closedPeriodId: UUID | null;
  newPeriodId: UUID | null;
}

// ---------------------------------------------------------------------------
// Request DTOs — mirror apps/.../hr/api/v2/dto/*Request.java
// (declared independently of the response types by design)
// ---------------------------------------------------------------------------

export interface CreatePersonRequest {
  firstName: string;
  middleName?: string;
  lastName: string;
}

export interface PatchPersonRequest {
  firstName: string;
  middleName?: string;
  lastName: string;
  /** Optimistic concurrency precondition (backend: @NotNull Long). */
  expectedVersion: number;
}

export interface PatchPersonPrivateRequest {
  dateOfBirth?: LocalDate;
  nationalityCountryCode?: string;
  maritalStatus?: "SINGLE" | "MARRIED" | "DIVORCED" | "WIDOWED" | "SEPARATED";
  /** Optimistic concurrency precondition (backend: @NotNull Long). */
  expectedVersion: number;
}

export interface AddIdentifierRequest {
  identifierType: string;
  issuingCountryCode?: string;
  /** Identifier value is write-only: the API never returns it back. */
  value: string;
}

export interface LinkUserRequest {
  userId: UUID;
}

export interface CreateEmploymentRequest {
  personId: UUID;
  legalEntityId: UUID;
  employeeNumber: string;
  employmentStartDate: LocalDate;
  laborJurisdictionCode: string;
  workerClassificationCode: string;
}

export interface LifecycleCommandRequest {
  effectiveDate: LocalDate;
  expectedVersion: number;
  reasonCode?: string;
}

export interface CreateAssignmentRequest {
  employmentId: UUID;
  organizationId: UUID;
  orgUnitId?: UUID;
  positionId?: UUID;
  reportsToAssignmentId?: UUID;
  assignmentType: AssignmentType;
  occupancyMode: OccupancyMode;
  allocationPercent?: number;
  effectiveFrom: LocalDate;
  effectiveTo?: LocalDate;
}

export interface EndAssignmentRequest {
  effectiveDate: LocalDate;
  expectedVersion: number;
  reasonCode?: string;
}

export interface ChangeManagerRequest {
  reportsToAssignmentId: UUID;
  effectiveDate: LocalDate;
  expectedVersion: number;
}

export interface TransferRequest {
  orgUnitId: UUID;
  positionId?: UUID;
  reportsToAssignmentId?: UUID;
  effectiveDate: LocalDate;
  expectedVersion: number;
}

export interface CreateOrgUnitRequest {
  organizationId: UUID;
  name: string;
  code: string;
  unitType: string;
  parentOrgUnitId?: UUID;
  effectiveFrom: LocalDate;
}

export interface ReviseOrgUnitRequest {
  parentOrgUnitId?: UUID;
  name?: string;
  code?: string;
  unitType?: string;
  effectiveDate: LocalDate;
}

export interface CreateJobRequest {
  organizationId: UUID;
  title: string;
  grade?: string;
  effectiveFrom: LocalDate;
}

export interface ReviseJobRequest {
  title?: string;
  grade?: string;
  effectiveDate: LocalDate;
}

export interface CreatePositionRequest {
  title: string;
  code?: string;
  jobId?: UUID;
  orgUnitId?: UUID;
  effectiveFrom: LocalDate;
}

export interface RevisePositionRequest {
  title?: string;
  jobId?: UUID;
  orgUnitId?: UUID;
  effectiveDate: LocalDate;
}

export interface CreateContractRequest {
  employmentId: UUID;
  contractNumber: string;
  isPrimary: boolean;
  contractTermType?: string;
  contractStartDate?: LocalDate;
  contractEndDate?: LocalDate;
  effectiveDate: LocalDate;
  documentReference?: string;
}

export interface AmendContractRequest {
  contractTermType?: string;
  contractStartDate?: LocalDate;
  contractEndDate?: LocalDate;
  effectiveDate: LocalDate;
  documentReference?: string;
  reasonCode?: string;
}

export interface ActivateContractRequest {
  versionNumber: number;
  effectiveDate: LocalDate;
}

export interface TerminateContractRequest {
  effectiveDate: LocalDate;
  reasonCode?: string;
}

export interface CompensationComponentInput {
  componentCode: string;
  componentType: string;
  recurring: boolean;
  amount?: number;
  percentage?: string;
}

export interface CreateCompensationRequest {
  employmentId: UUID;
  currencyCode: string;
  payFrequency?: string;
  effectiveFrom: LocalDate;
  components?: CompensationComponentInput[];
}

export interface ReviseCompensationRequest {
  currencyCode?: string;
  payFrequency?: string;
  effectiveFrom: LocalDate;
  components?: CompensationComponentInput[];
  reasonCode?: string;
}

export interface EndCompensationRequest {
  effectiveTo: LocalDate;
}

export interface CreateOverrideRequest {
  complianceRuleId: UUID;
  resourceType: string;
  resourceId: UUID;
  justification: string;
  evidenceReference?: string;
  validFrom?: LocalDate;
  validUntil?: LocalDate;
}

export interface OverrideDecisionRequest {
  comment: string;
}

// ---------------------------------------------------------------------------
// Canonical v2 error envelope (WS5 Task 2)
// ---------------------------------------------------------------------------

export interface HrmViolation {
  field: string;
  message: string;
}

/** Typed view of the backend `HrApiErrorResponse` (HRM_* codes). */
export class HrmV2ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly violations: HrmViolation[] | null;
  readonly requestId: string | null;

  constructor(code: string, status: number, message: string | null, violations: HrmViolation[] | null, requestId: string | null) {
    super(message ?? code);
    this.name = "HrmV2ApiError";
    this.code = code;
    this.status = status;
    this.violations = violations;
    this.requestId = requestId;
  }
}

/**
 * Extract a typed `HrmV2ApiError` from a thrown value when the backend
 * produced the canonical structured envelope. Returns `null` for anything
 * else so callers can fall back to the generic user-facing error mapping.
 */
export function parseHrmV2Error(err: unknown): HrmV2ApiError | null {
  if (err === null || typeof err !== "object") return null;
  const details = (err as { details?: { status?: unknown; body?: unknown; requestId?: unknown } }).details;
  if (!details || typeof details !== "object") return null;
  const body = details.body;
  if (body === null || typeof body !== "object") return null;
  const code = (body as { code?: unknown }).code;
  if (typeof code !== "string" || !code.startsWith("HRM_")) return null;
  const rawViolations = (body as { violations?: unknown }).violations;
  let violations: HrmViolation[] | null = null;
  if (Array.isArray(rawViolations)) {
    violations = rawViolations
      .filter((v): v is HrmViolation =>
        v !== null && typeof v === "object" &&
        typeof (v as { field?: unknown }).field === "string" &&
        typeof (v as { message?: unknown }).message === "string")
      .map((v) => ({ field: v.field, message: v.message }));
  }
  const message = (body as { message?: unknown }).message;
  return new HrmV2ApiError(
    code,
    typeof details.status === "number" ? details.status : 0,
    typeof message === "string" ? message : null,
    violations,
    typeof details.requestId === "string" ? details.requestId : null,
  );
}

/** Generate a fresh UUID-shaped Idempotency-Key (crypto-backed, with fallback). */
export function newIdempotencyKey(): string {
  const c = typeof crypto !== "undefined" ? crypto : undefined;
  if (c && typeof c.randomUUID === "function") return c.randomUUID();
  // RFC 4122 v4 fallback for non-secure contexts without randomUUID.
  const hex = "0123456789abcdef";
  let out = "";
  for (let i = 0; i < 36; i++) {
    if (i === 8 || i === 13 || i === 18 || i === 23) out += "-";
    else if (i === 14) out += "4";
    else if (i === 19) out += hex[(Math.floor(Math.random() * 16) & 0x3) | 0x8];
    else out += hex[Math.floor(Math.random() * 16)];
  }
  return out;
}

// ---------------------------------------------------------------------------
// Client — one method per canonical v2 operation (58 total).
// ---------------------------------------------------------------------------

type ReqOptions = { signal?: AbortSignal; timeoutMs?: number };

function headersWithIdempotency(idempotencyKey: string): Record<string, string> {
  return { "Idempotency-Key": idempotencyKey };
}

function build(path: string): string {
  return `${HRM_V2_BASE}${path}`;
}

export const hrmV2Api = {
  // ==================== People (9) ====================

  listPeople(options?: ReqOptions) {
    return apiClient.request<PersonSummaryResponse[]>({ method: "GET", path: build("/people"), ...options });
  },

  createPerson(request: CreatePersonRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<PersonSummaryResponse, CreatePersonRequest>({
      method: "POST", path: build("/people"), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  getPerson(personId: UUID, options?: ReqOptions) {
    return apiClient.request<PersonSummaryResponse>({ method: "GET", path: build(`/people/${personId}`), ...options });
  },

  patchPerson(personId: UUID, request: PatchPersonRequest, options?: ReqOptions) {
    return apiClient.request<PersonSummaryResponse, PatchPersonRequest>({
      method: "PATCH", path: build(`/people/${personId}`), body: request, ...options,
    });
  },

  /** Audited private PII read — capability HRM.PII.VIEW, generates audit evidence. */
  getPersonPrivate(personId: UUID, options?: ReqOptions) {
    return apiClient.request<PersonPrivateResponse>({ method: "GET", path: build(`/people/${personId}/private`), ...options });
  },

  patchPersonPrivate(personId: UUID, request: PatchPersonPrivateRequest, options?: ReqOptions) {
    return apiClient.request<PersonPrivateMutationResponse, PatchPersonPrivateRequest>({
      method: "PATCH", path: build(`/people/${personId}/private`), body: request, ...options,
    });
  },

  /** Identifier values are write-only; the response carries metadata only. */
  addIdentifier(personId: UUID, request: AddIdentifierRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<IdentifierMetadataResponse, AddIdentifierRequest>({
      method: "POST", path: build(`/people/${personId}/identifiers`), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  linkUser(personId: UUID, request: LinkUserRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<PersonLinkResponse, LinkUserRequest>({
      method: "POST", path: build(`/people/${personId}/user-link`), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  unlinkUser(personId: UUID, options?: ReqOptions) {
    return apiClient.request<void>({ method: "DELETE", path: build(`/people/${personId}/user-link`), ...options });
  },

  // ==================== Employments (11) ====================

  listEmployments(options?: ReqOptions) {
    return apiClient.request<EmploymentResponse[]>({ method: "GET", path: build("/employments"), ...options });
  },

  createEmployment(request: CreateEmploymentRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<EmploymentResponse, CreateEmploymentRequest>({
      method: "POST", path: build("/employments"), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  getEmployment(employmentId: UUID, options?: ReqOptions) {
    return apiClient.request<EmploymentResponse>({ method: "GET", path: build(`/employments/${employmentId}`), ...options });
  },

  /** Lifecycle commands: submit-onboarding, activate, start-leave,
   *  return-from-leave, suspend, reinstate, terminate, void. */
  employmentLifecycle(
    employmentId: UUID,
    command: "submit-onboarding" | "activate" | "start-leave" | "return-from-leave" | "suspend" | "reinstate" | "terminate" | "void",
    request: LifecycleCommandRequest,
    idempotencyKey: string,
    options?: ReqOptions,
  ) {
    return apiClient.request<LifecycleCommandResponse, LifecycleCommandRequest>({
      method: "POST", path: build(`/employments/${employmentId}/${command}`), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  // ==================== Assignments (6) ====================

  listAssignments(options?: ReqOptions) {
    return apiClient.request<AssignmentResponse[]>({ method: "GET", path: build("/assignments"), ...options });
  },

  createAssignment(request: CreateAssignmentRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<AssignmentResponse, CreateAssignmentRequest>({
      method: "POST", path: build("/assignments"), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  getAssignment(assignmentId: UUID, options?: ReqOptions) {
    return apiClient.request<AssignmentResponse>({ method: "GET", path: build(`/assignments/${assignmentId}`), ...options });
  },

  endAssignment(assignmentId: UUID, request: EndAssignmentRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<AssignmentResponse, EndAssignmentRequest>({
      method: "POST", path: build(`/assignments/${assignmentId}/end`), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  changeAssignmentManager(assignmentId: UUID, request: ChangeManagerRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<AssignmentResponse, ChangeManagerRequest>({
      method: "POST", path: build(`/assignments/${assignmentId}/change-manager`), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  transferAssignment(assignmentId: UUID, request: TransferRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<AssignmentResponse, TransferRequest>({
      method: "POST", path: build(`/assignments/${assignmentId}/transfer`), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  // ==================== Org Units (4) ====================

  listOrgUnits(options?: ReqOptions) {
    return apiClient.request<OrgUnitResponse[]>({ method: "GET", path: build("/org-units"), ...options });
  },

  createOrgUnit(request: CreateOrgUnitRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<OrgUnitResponse, CreateOrgUnitRequest>({
      method: "POST", path: build("/org-units"), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  getOrgUnit(orgUnitId: UUID, options?: ReqOptions) {
    return apiClient.request<OrgUnitResponse>({ method: "GET", path: build(`/org-units/${orgUnitId}`), ...options });
  },

  reviseOrgUnit(orgUnitId: UUID, request: ReviseOrgUnitRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<OrgUnitResponse, ReviseOrgUnitRequest>({
      method: "POST", path: build(`/org-units/${orgUnitId}/revise`), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  // ==================== Jobs (4) ====================

  listJobs(options?: ReqOptions) {
    return apiClient.request<JobResponse[]>({ method: "GET", path: build("/jobs"), ...options });
  },

  createJob(request: CreateJobRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<JobResponse, CreateJobRequest>({
      method: "POST", path: build("/jobs"), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  getJob(jobId: UUID, options?: ReqOptions) {
    return apiClient.request<JobResponse>({ method: "GET", path: build(`/jobs/${jobId}`), ...options });
  },

  reviseJob(jobId: UUID, request: ReviseJobRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<JobResponse, ReviseJobRequest>({
      method: "POST", path: build(`/jobs/${jobId}/revise`), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  // ==================== Positions (6) ====================

  listPositions(options?: ReqOptions) {
    return apiClient.request<PositionResponse[]>({ method: "GET", path: build("/positions"), ...options });
  },

  createPosition(request: CreatePositionRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<PositionResponse, CreatePositionRequest>({
      method: "POST", path: build("/positions"), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  getPosition(positionId: UUID, options?: ReqOptions) {
    return apiClient.request<PositionResponse>({ method: "GET", path: build(`/positions/${positionId}`), ...options });
  },

  revisePosition(positionId: UUID, request: RevisePositionRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<PositionResponse, RevisePositionRequest>({
      method: "POST", path: build(`/positions/${positionId}/revise`), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  freezePosition(positionId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<PositionResponse>({
      method: "POST", path: build(`/positions/${positionId}/freeze`),
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  closePosition(positionId: UUID, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<PositionResponse>({
      method: "POST", path: build(`/positions/${positionId}/close`),
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  // ==================== Contracts (6) ====================

  listContracts(options?: ReqOptions) {
    return apiClient.request<ContractResponse[]>({ method: "GET", path: build("/contracts"), ...options });
  },

  createContract(request: CreateContractRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<ContractResponse, CreateContractRequest>({
      method: "POST", path: build("/contracts"), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  getContract(contractId: UUID, options?: ReqOptions) {
    return apiClient.request<ContractResponse>({ method: "GET", path: build(`/contracts/${contractId}`), ...options });
  },

  amendContract(contractId: UUID, request: AmendContractRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<ContractResponse, AmendContractRequest>({
      method: "POST", path: build(`/contracts/${contractId}/amend`), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  activateContract(contractId: UUID, request: ActivateContractRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<ContractResponse, ActivateContractRequest>({
      method: "POST", path: build(`/contracts/${contractId}/activate`), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  terminateContract(contractId: UUID, request: TerminateContractRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<ContractResponse, TerminateContractRequest>({
      method: "POST", path: build(`/contracts/${contractId}/terminate`), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  // ==================== Compensation (5) ====================

  /** Restricted surface — requires HRM.COMPENSATION.VIEW server-side. */
  listCompensationPackages(employmentId: UUID, options?: ReqOptions) {
    return apiClient.request<CompensationPackageResponse[]>({
      method: "GET", path: build("/compensation-packages"), query: { employmentId }, ...options,
    });
  },

  createCompensationPackage(request: CreateCompensationRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<CompensationPackageResponse, CreateCompensationRequest>({
      method: "POST", path: build("/compensation-packages"), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  getCompensationPackage(packageId: UUID, options?: ReqOptions) {
    return apiClient.request<CompensationPackageResponse>({ method: "GET", path: build(`/compensation-packages/${packageId}`), ...options });
  },

  reviseCompensationPackage(packageId: UUID, request: ReviseCompensationRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<CompensationPackageResponse, ReviseCompensationRequest>({
      method: "POST", path: build(`/compensation-packages/${packageId}/revise`), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  endCompensationPackage(packageId: UUID, request: EndCompensationRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<CompensationPackageResponse, EndCompensationRequest>({
      method: "POST", path: build(`/compensation-packages/${packageId}/end`), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  // ==================== Compliance (6) ====================

  getComplianceContext(employmentId: UUID, effectiveDate?: LocalDate, options?: ReqOptions) {
    return apiClient.request<ComplianceContextResponse>({
      method: "GET", path: build("/compliance/context"),
      query: { employmentId, ...(effectiveDate ? { effectiveDate } : {}) }, ...options,
    });
  },

  listComplianceOverrides(options?: ReqOptions) {
    return apiClient.request<OverrideRequestResponse[]>({ method: "GET", path: build("/compliance/overrides"), ...options });
  },

  requestComplianceOverride(request: CreateOverrideRequest, idempotencyKey: string, options?: ReqOptions) {
    return apiClient.request<OverrideRequestResponse, CreateOverrideRequest>({
      method: "POST", path: build("/compliance/overrides"), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  /** decision: approve | reject | revoke — all four-eyes enforced backend-side. */
  decideComplianceOverride(
    overrideId: UUID,
    decision: "approve" | "reject" | "revoke",
    request: OverrideDecisionRequest,
    idempotencyKey: string,
    options?: ReqOptions,
  ) {
    return apiClient.request<OverrideRequestResponse, OverrideDecisionRequest>({
      method: "POST", path: build(`/compliance/overrides/${overrideId}/${decision}`), body: request,
      context: { headers: headersWithIdempotency(idempotencyKey) }, ...options,
    });
  },

  // ==================== Audit (1) ====================

  listAudit(params?: { resourceType?: string; limit?: number }, options?: ReqOptions) {
    return apiClient.request<AuditEntryResponse[]>({
      method: "GET", path: build("/audit"),
      query: {
        ...(params?.resourceType ? { resourceType: params.resourceType } : {}),
        ...(params?.limit !== undefined ? { limit: params.limit } : {}),
      },
      ...options,
    });
  },
};
