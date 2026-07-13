/**
 * SNAD CRM API — Generated TypeScript types.
 * ----------------------------------------------------------------------------
 * DO NOT EDIT BY HAND. This file is generated from
 *   docs/crm/contracts/openapi/crm-openapi.json
 * by the `npm run crm:generate-api-types` script (see
 *   scripts/crm/generate-crm-api-types.sh).
 *
 * Regeneration command (from apps/web):
 *   npm run crm:generate-api-types
 *
 * Branch: crm/003-stable-api-contracts
 * Gate:   CRM-G2 — API Contract and Concurrency Gate
 *
 * OpenAPI checksum (sha256, first 16 hex chars): c71e950d25d7d593
 * Generated at: 2026-07-13
 */

// ──────────────────────────────────────────────────────────────────────
// Common
// ──────────────────────────────────────────────────────────────────────

export interface Meta {
  requestId: string;
  timestamp: string;
}

export interface Page {
  nextCursor: string | null;
  hasMore: boolean;
  limit: number;
}

export interface FieldError {
  field: string;
  code: string;
  message: string;
}

export interface CrmErrorBody {
  code: string;
  message: string;
  status: number;
  requestId: string;
  timestamp: string;
  fieldErrors?: FieldError[];
  details?: Record<string, unknown>;
}

export interface ErrorResponse {
  error: CrmErrorBody;
}

// ──────────────────────────────────────────────────────────────────────
// Response envelopes
// ──────────────────────────────────────────────────────────────────────

export interface SingleResponse<T> {
  data: T;
  meta: Meta;
}

export interface ListResponse<T> {
  data: T[];
  page: Page;
  meta: Meta;
}

// ──────────────────────────────────────────────────────────────────────
// Accounts
// ──────────────────────────────────────────────────────────────────────

export type AccountType = "BUSINESS" | "PERSON" | "PARTNER" | "PROSPECT" | "OTHER";

export interface CreateAccountRequest {
  displayName: string;
  accountType?: AccountType;
  ownerUserId?: string;
  parentAccountId?: string;
  primaryCurrencyCode?: string;
  preferredLocale?: string;
  timeZone?: string;
  source?: string;
}

export interface UpdateAccountRequest {
  displayName?: string;
  ownerUserId?: string;
  parentAccountId?: string;
  primaryCurrencyCode?: string;
  preferredLocale?: string;
  timeZone?: string;
  source?: string;
}

export interface AccountResponse {
  id: string;
  version: number;
  displayName: string;
  normalizedDisplayName?: string;
  accountType: string;
  lifecycleStatus: string;
  primaryCurrencyCode?: string;
  preferredLocale?: string;
  timeZone?: string;
  source?: string;
  parentAccountId?: string | null;
  ownerUserId?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface ArchiveAccountResponse {
  id: string;
  version: number;
  lifecycleStatus: string;
  updatedAt: string;
}

export type AccountSingleResponse = SingleResponse<AccountResponse>;
export type AccountListResponse = ListResponse<AccountResponse>;

// ──────────────────────────────────────────────────────────────────────
// Contacts
// ──────────────────────────────────────────────────────────────────────

export interface CreateContactRequest {
  accountId?: string;
  givenName: string;
  familyName?: string;
  primaryEmail?: string;
  primaryPhone?: string;
  preferredLocale?: string;
  timeZone?: string;
  ownerUserId?: string;
  consentSummary?: "UNKNOWN" | "GRANTED" | "DENIED" | "WITHDRAWN";
}

export interface UpdateContactRequest {
  accountId?: string;
  givenName?: string;
  familyName?: string;
  primaryEmail?: string;
  primaryPhone?: string;
  preferredLocale?: string;
  timeZone?: string;
  ownerUserId?: string;
  consentSummary?: "UNKNOWN" | "GRANTED" | "DENIED" | "WITHDRAWN";
}

export interface ContactResponse {
  id: string;
  version: number;
  accountId?: string;
  givenName?: string;
  familyName?: string;
  displayName?: string;
  primaryEmail?: string;
  normalizedEmail?: string;
  primaryPhone?: string;
  preferredLocale?: string;
  timeZone?: string;
  lifecycleStatus?: string;
  ownerUserId?: string;
  consentSummary?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ContactSummaryResponse {
  id: string;
  accountId?: string;
  displayName?: string;
  primaryEmail?: string;
  primaryPhone?: string;
  lifecycleStatus?: string;
  updatedAt?: string;
}

// ──────────────────────────────────────────────────────────────────────
// Leads
// ──────────────────────────────────────────────────────────────────────

export type LeadStatus = "NEW" | "ASSIGNED" | "CONTACTED" | "QUALIFIED" | "DISQUALIFIED" | "ARCHIVED" | "CONVERTED";

export interface CreateLeadRequest {
  displayName: string;
  companyName?: string;
  email?: string;
  phone?: string;
  source?: string;
  ownerUserId?: string;
  queueId?: string;
  score?: number;
}

export interface ConvertLeadRequest {
  accountName?: string;
  createOpportunity?: boolean;
  pipelineId?: string;
  stageId?: string;
  opportunityName?: string;
  amount?: number;
  currencyCode?: string;
  expectedCloseDate?: string;
}

export interface LeadResponse {
  id: string;
  version: number;
  displayName: string;
  companyName?: string;
  email?: string;
  phone?: string;
  source?: string;
  status: LeadStatus;
  ownerUserId?: string;
  score?: number;
  convertedAccountId?: string | null;
  convertedContactId?: string | null;
  convertedOpportunityId?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface LeadConversionResponse {
  lead: LeadResponse;
  account: AccountResponse | null;
  contact: ContactResponse | null;
  opportunity: OpportunityResponse | null;
  idempotent: boolean;
}

// ──────────────────────────────────────────────────────────────────────
// Pipelines & Stages
// ──────────────────────────────────────────────────────────────────────

export interface CreatePipelineRequest {
  name: string;
  currencyCode: string;
  stages: string[];
}

export interface PipelineResponse {
  id: string;
  version: number;
  name: string;
  currencyCode: string;
  active: boolean;
  stages: StageResponse[];
  createdAt?: string;
  updatedAt?: string;
}

export interface StageResponse {
  id: string;
  pipelineId: string;
  name: string;
  sequence: number;
  probability?: number;
  terminalState?: string | null;
  active: boolean;
}

// ──────────────────────────────────────────────────────────────────────
// Opportunities
// ──────────────────────────────────────────────────────────────────────

export interface CreateOpportunityRequest {
  accountId: string;
  contactId?: string;
  pipelineId: string;
  stageId: string;
  name: string;
  amount?: number;
  currencyCode: string;
  expectedCloseDate?: string;
  ownerUserId?: string;
}

export interface MoveOpportunityStageRequest {
  stageId: string;
  status?: "OPEN" | "CANCELLED";
  reason?: string;
}

export interface OpportunityResponse {
  id: string;
  version: number;
  accountId: string;
  contactId?: string;
  pipelineId: string;
  stageId: string;
  name: string;
  amount?: number;
  currencyCode: string;
  probability?: number;
  status: string;
  winLossReason?: string;
  expectedCloseDate?: string;
  ownerUserId?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface OpportunitySummaryResponse {
  id: string;
  accountId: string;
  name: string;
  amount?: number;
  currencyCode: string;
  status: string;
  updatedAt?: string;
}

// ──────────────────────────────────────────────────────────────────────
// Activities
// ──────────────────────────────────────────────────────────────────────

export type ActivityType = "TASK" | "CALL" | "MEETING" | "EMAIL" | "NOTE" | "MESSAGE" | "OTHER";

export interface CreateActivityRequest {
  activityType: ActivityType;
  subject: string;
  body?: string;
  relatedType?: "ACCOUNT" | "CONTACT" | "LEAD" | "OPPORTUNITY";
  relatedId?: string;
  ownerUserId?: string;
  priority?: number;
  startAt?: string;
  dueAt?: string;
}

export interface CompleteActivityRequest {
  result?: string;
}

export interface ActivityResponse {
  id: string;
  version: number;
  activityType: string;
  subject: string;
  body?: string;
  relatedType?: string;
  relatedId?: string;
  ownerUserId?: string;
  status: string;
  priority?: number;
  startAt?: string;
  dueAt?: string;
  completedAt?: string;
  result?: string;
  createdAt?: string;
  updatedAt?: string;
}

// ──────────────────────────────────────────────────────────────────────
// Timeline
// ──────────────────────────────────────────────────────────────────────

export interface TimelineEventResponse {
  id: string;
  subjectType: string;
  subjectId: string;
  eventType: string;
  summary: string;
  sourceType?: string;
  sourceId?: string;
  occurredAt: string;
  createdBy?: string;
}

// ──────────────────────────────────────────────────────────────────────
// Imports
// ──────────────────────────────────────────────────────────────────────

export interface ImportJobResponse {
  id: string;
  entityType: string;
  fileName?: string;
  totalRows: number;
  processedRows: number;
  successfulRows: number;
  failedRows: number;
  status: string;
  mappingJson?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ImportErrorResponse {
  id: string;
  jobId: string;
  rowNumber: number;
  errorCode?: string;
  errorMessage?: string;
  rowData?: Record<string, unknown>;
}

export interface ImportRunResponse {
  jobId: string;
  status: string;
  processedRows: number;
  successfulRows: number;
  failedRows: number;
  updatedAt: string;
}

// ──────────────────────────────────────────────────────────────────────
// Custom Fields
// ──────────────────────────────────────────────────────────────────────

export interface CreateCustomFieldRequest {
  entityType: "ACCOUNT" | "CONTACT" | "LEAD" | "OPPORTUNITY" | "ACTIVITY";
  fieldKey: string;
  labelAr: string;
  labelEn: string;
  dataType: "TEXT" | "NUMBER" | "BOOLEAN" | "DATE" | "DATETIME" | "EMAIL" | "URL";
  sensitive?: boolean;
  searchable?: boolean;
  required?: boolean;
}

export interface CustomFieldResponse {
  id: string;
  entityType: string;
  fieldKey: string;
  labelAr: string;
  labelEn: string;
  dataType: string;
  sensitive: boolean;
  searchable: boolean;
  required: boolean;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface CustomFieldValuesResponse {
  entityType: string;
  entityId: string;
  values: Record<string, unknown>;
}

export interface UpsertCustomFieldValuesRequest {
  values: Record<string, unknown>;
}

// ──────────────────────────────────────────────────────────────────────
// Customer 360
// ──────────────────────────────────────────────────────────────────────

export interface Customer360Response {
  account: AccountResponse;
  contacts: ContactSummaryResponse[];
  opportunities: OpportunitySummaryResponse[];
  activities: ActivitySummaryResponse[];
  timeline: TimelineEventResponse[];
  customFields: Record<string, unknown>;
}

export interface ActivitySummaryResponse {
  id: string;
  activityType: string;
  subject: string;
  status: string;
  updatedAt: string;
}
