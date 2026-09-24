import { apiClient } from "./client";

export interface CrmDashboard {
  accounts: number;
  contacts: number;
  openLeads: number;
  openOpportunities: number;
  weightedPipeline: number;
  overdueActivities: number;
  recentActivity: CrmTimelineEvent[];
}

export interface CrmAccount {
  id: string;
  display_name: string;
  account_type: string;
  lifecycle_status: string;
  primary_currency_code?: string | null;
  owner_user_id?: string | null;
  updated_at: string;
}

export interface CrmContact {
  id: string;
  account_id?: string | null;
  given_name: string;
  family_name?: string | null;
  display_name: string;
  primary_email?: string | null;
  primary_phone?: string | null;
  consent_summary: string;
  lifecycle_status: string;
  updated_at: string;
}

export interface CrmLead {
  id: string;
  display_name: string;
  company_name?: string | null;
  email?: string | null;
  phone?: string | null;
  source?: string | null;
  status: string;
  score?: number | null;
  updated_at: string;
}

export interface CrmPipeline {
  id: string;
  name: string;
  currency_code?: string | null;
  active: boolean;
}

export interface CrmStage {
  id: string;
  pipeline_id: string;
  name: string;
  sequence: number;
  probability: number;
  terminal_state?: string | null;
  active: boolean;
}

export interface CrmOpportunity {
  id: string;
  account_id: string;
  contact_id?: string | null;
  pipeline_id: string;
  stage_id: string;
  name: string;
  amount?: number | null;
  currency_code: string;
  probability: number;
  status: string;
  expected_close_date?: string | null;
  updated_at: string;
}

export interface CrmActivity {
  id: string;
  version?: number;
  activity_type: string;
  subject: string;
  body?: string | null;
  related_type?: string | null;
  related_id?: string | null;
  owner_user_id?: string | null;
  status: string;
  priority: number;
  start_at?: string | null;
  due_at?: string | null;
  completed_at?: string | null;
  result?: string | null;
  created_at?: string | null;
  updated_at: string;
}

export interface CrmTimelineEvent {
  id: string;
  subject_type: string;
  subject_id: string;
  event_type: string;
  summary: string;
  occurred_at: string;
}

export interface Customer360 {
  account: CrmAccount;
  contacts: CrmContact[];
  opportunities: Array<CrmOpportunity & { pipeline_name?: string; stage_name?: string }>;
  activities: CrmActivity[];
  timeline: CrmTimelineEvent[];
}

/**
 * CRM import job — frontend representation.
 * TD-002-2: Now fetched from V2 (/api/v2/crm/imports) but mapped to V1 snake_case
 * field names for backward compatibility with consuming components.
 */
export interface CrmImportJob {
  id: string;
  entityType: string;
  status: string;
  totalRows?: number | null;
  processedRows?: number | null;
  succeededRows?: number | null;
  failedRows?: number | null;
  fileName?: string | null;
  uploadedAt?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  errorMessage?: string | null;
}

export interface CrmImportErrorRow {
  rowNumber: number;
  rawData?: string | null;
  errorMessage?: string | null;
  fieldErrors?: Record<string, string> | null;
}

/**
 * CRM custom field definition — frontend representation.
 * TD-002-2: Now fetched from V2 (/api/v2/crm/custom-fields) but mapped to V1
 * camelCase field names for backward compatibility.
 */
export interface CrmCustomField {
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
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface CrmCustomFieldValueEntry {
  fieldKey: string;
  value: unknown;
  displayValue?: string | null;
  sensitive?: boolean;
}

export interface CrmCustomFieldValues {
  entityType: string;
  entityId: string;
  values: CrmCustomFieldValueEntry[];
}

/**
 * CRM Tag — reusable label that can be assigned to any CRM entity.
 * Branch: feature/crm-tags
 */
export interface CrmTag {
  id: string;
  version: number;
  name: string;
  color?: string | null;
  created_at: string;
  updated_at: string;
}

export interface CrmTagAssignment {
  id: string;
  tag_id: string;
  tag_name: string;
  tag_color?: string | null;
  subject_type: string;
  subject_id: string;
  assigned_by?: string | null;
  assigned_at: string;
}

/**
 * CRM Note — plain-text note attached to any CRM entity.
 * Branch: feature/crm-notes
 */
export interface CrmNote {
  id: string;
  version: number;
  subject_type: string;
  subject_id: string;
  body: string;
  author_user_id?: string | null;
  archived: boolean;
  created_at: string;
  updated_at: string;
}

/**
 * CRM Task — first-class work item.
 * Branch: feature/crm-tasks
 */
export interface CrmTask {
  id: string;
  version: number;
  title: string;
  description?: string | null;
  related_type?: string | null;
  related_id?: string | null;
  assignee_user_id?: string | null;
  owner_user_id?: string | null;
  status: string;
  priority: number;
  start_at?: string | null;
  due_at?: string | null;
  completed_at?: string | null;
  result?: string | null;
  created_at: string;
  updated_at: string;
}

export interface CrmCase {
  id: string;
  version: number;
  subject: string;
  description?: string | null;
  case_type?: string | null;
  status: string;
  priority: number;
  customer_id?: string | null;
  assignee_user_id?: string | null;
  owner_user_id?: string | null;
  related_id?: string | null;
  due_at?: string | null;
  resolved_at?: string | null;
  closed_at?: string | null;
  created_at: string;
  updated_at: string;
}

/**
 * TD-002-2 — V1 API root.
 *
 * The V1 CRM surface is deprecated (see V1DeprecationHeaderFilter on the
 * backend). Methods below that have a verified V2 equivalent are migrated to
 * the {@code v2root} constant; methods that have NO V2 equivalent (dashboard,
 * createPipeline, notes, tasks, reports, search, export, custom-field
 * sensitive read) remain on V1 until TD-006 builds the missing V2 surface.
 */
const root = "/api/v1/crm";

/** V2 CRM API root — used by the 30 migrated methods. */
const v2root = "/api/v2/crm";

// ──────────────────────────────────────────────────────────────────────────
// TD-002-2 V2 Response Adapters
//
// The V2 CRM API returns camelCase typed DTOs wrapped in SingleResponse<T> /
// ListResponse<T> envelopes. The V1 API returns snake_case raw Maps. To
// preserve backward compatibility with the 25+ consuming components (which
// all expect V1 snake_case shapes), these adapter functions unwrap the V2
// envelope and map camelCase fields back to snake_case.
// ──────────────────────────────────────────────────────────────────────────

/** V2 SingleResponse envelope: { data: T, meta: { requestId, timestamp } } */
interface V2SingleResponse<T> { data: T; meta: { requestId?: string; timestamp?: string } }
/** V2 ListResponse envelope: { data: T[], page: { nextCursor, hasMore, limit }, meta } */
interface V2ListResponse<T> { data: T[]; page?: { nextCursor?: string | null; hasMore?: boolean; limit?: number }; meta?: { requestId?: string; timestamp?: string } }

/** Unwrap a V2 SingleResponse envelope and return .data */
async function unwrapSingle<T>(promise: Promise<V2SingleResponse<T>>): Promise<T> {
  const res = await promise;
  return res.data;
}

/**
 * Fetch all pages from a V2 cursor-paginated list endpoint.
 * V2 uses limit+1 probing with nextCursor; V1 used offset/limit=200.
 * This adapter exhausts all cursors to return a flat array (matching V1 behavior).
 */
async function fetchAllPages<T>(fetchPage: (cursor?: string) => Promise<V2ListResponse<T>>): Promise<T[]> {
  const all: T[] = [];
  let cursor: string | undefined = undefined;
  // Safety limit to avoid infinite loops on broken cursors.
  for (let i = 0; i < 50; i++) {
    const res = await fetchPage(cursor);
    if (Array.isArray(res.data)) all.push(...res.data);
    if (!res.page?.hasMore || !res.page?.nextCursor) break;
    cursor = res.page.nextCursor;
  }
  return all;
}

// ── V2 camelCase → V1 snake_case field mappers ──────────────────────────

function mapV2Account(a: V2AccountResponse): CrmAccount {
  return {
    id: a.id,
    display_name: a.displayName,
    account_type: a.accountType,
    lifecycle_status: a.lifecycleStatus,
    primary_currency_code: a.primaryCurrencyCode ?? null,
    owner_user_id: a.ownerUserId ?? null,
    updated_at: a.updatedAt,
  };
}

function mapV2Contact(c: V2ContactResponse): CrmContact {
  return {
    id: c.id,
    account_id: c.accountId ?? null,
    given_name: c.givenName,
    family_name: c.familyName ?? null,
    display_name: c.displayName,
    primary_email: c.primaryEmail ?? null,
    primary_phone: c.primaryPhone ?? null,
    consent_summary: c.consentSummary,
    lifecycle_status: c.lifecycleStatus,
    updated_at: c.updatedAt,
  };
}

function mapV2Lead(l: V2LeadResponse): CrmLead {
  return {
    id: l.id,
    display_name: l.displayName,
    company_name: l.companyName ?? null,
    email: l.email ?? null,
    phone: l.phone ?? null,
    source: l.source ?? null,
    status: l.status,
    score: l.score ?? null,
    updated_at: l.updatedAt,
  };
}

function mapV2Opportunity(o: V2OpportunityResponse): CrmOpportunity {
  return {
    id: o.id,
    account_id: o.accountId,
    contact_id: o.contactId ?? null,
    pipeline_id: o.pipelineId,
    stage_id: o.stageId,
    name: o.name,
    amount: o.amount ?? null,
    currency_code: o.currencyCode,
    probability: o.probability,
    status: o.status,
    expected_close_date: o.expectedCloseDate ?? null,
    updated_at: o.updatedAt,
  };
}

function mapV2Activity(a: V2ActivityResponse): CrmActivity {
  return {
    id: a.id,
    version: a.version,
    activity_type: a.activityType,
    subject: a.subject,
    body: a.body ?? null,
    related_type: a.relatedType ?? null,
    related_id: a.relatedId ?? null,
    owner_user_id: a.ownerUserId ?? null,
    status: a.status,
    priority: a.priority,
    start_at: a.startAt ?? null,
    due_at: a.dueAt ?? null,
    completed_at: a.completedAt ?? null,
    result: a.result ?? null,
    created_at: a.createdAt ?? null,
    updated_at: a.updatedAt,
  };
}

function mapV2Pipeline(p: V2PipelineResponse): CrmPipeline {
  return {
    id: p.id,
    name: p.name,
    currency_code: p.currencyCode ?? null,
    active: p.active,
  };
}

function mapV2Stage(s: V2StageResponse): CrmStage {
  return {
    id: s.id,
    pipeline_id: s.pipelineId,
    name: s.name,
    sequence: s.sequence,
    probability: s.probability,
    terminal_state: s.terminalState ?? null,
    active: s.active,
  };
}

function mapV2TimelineEvent(e: V2TimelineEventResponse): CrmTimelineEvent {
  return {
    id: e.id,
    subject_type: e.subjectType,
    subject_id: e.subjectId,
    event_type: e.eventType,
    summary: e.summary,
    occurred_at: e.occurredAt,
  };
}

function mapV2ImportJob(j: V2ImportJobResponse): CrmImportJob {
  return {
    id: j.id,
    entityType: j.entityType,
    status: j.status,
    totalRows: j.totalRows ?? null,
    processedRows: j.processedRows ?? null,
    succeededRows: j.succeededRows ?? null,
    failedRows: j.failedRows ?? null,
    fileName: j.fileName ?? null,
    uploadedAt: j.uploadedAt ?? null,
    startedAt: j.startedAt ?? null,
    completedAt: j.completedAt ?? null,
    errorMessage: j.errorMessage ?? null,
  };
}

function mapV2CustomField(cf: V2CustomFieldResponse): CrmCustomField {
  return {
    id: cf.id,
    entityType: cf.entityType,
    fieldKey: cf.fieldKey,
    labelAr: cf.labelAr,
    labelEn: cf.labelEn,
    dataType: cf.dataType,
    sensitive: cf.sensitive,
    searchable: cf.searchable,
    required: cf.required,
    active: cf.active,
    createdAt: cf.createdAt ?? null,
    updatedAt: cf.updatedAt ?? null,
  };
}

function mapV2Tag(t: V2TagResponse): CrmTag {
  return {
    id: t.id,
    version: t.version,
    name: t.name,
    color: t.color ?? null,
    created_at: t.createdAt,
    updated_at: t.updatedAt,
  };
}

function mapV2TagAssignment(a: V2TagAssignmentResponse): CrmTagAssignment {
  return {
    id: a.id,
    tag_id: a.tagId,
    tag_name: a.tagName,
    tag_color: a.tagColor ?? null,
    subject_type: a.subjectType,
    subject_id: a.subjectId,
    assigned_by: a.assignedBy ?? null,
    assigned_at: a.assignedAt,
  };
}

function mapV2Case(c: V2CaseResponse): CrmCase {
  return {
    id: c.id,
    version: c.version,
    subject: c.subject,
    description: c.description ?? null,
    case_type: c.caseType ?? null,
    status: c.status,
    priority: c.priority,
    customer_id: c.customerId ?? null,
    assignee_user_id: c.assigneeUserId ?? null,
    owner_user_id: c.ownerUserId ?? null,
    related_id: c.relatedId ?? null,
    due_at: c.dueAt ?? null,
    resolved_at: c.resolvedAt ?? null,
    closed_at: c.closedAt ?? null,
    created_at: c.createdAt,
    updated_at: c.updatedAt,
  };
}

// ── V2 typed DTO interfaces (camelCase, matching backend CrmDtos.java) ──

interface V2AccountResponse {
  id: string; version: number; displayName: string; accountType: string;
  lifecycleStatus: string; primaryCurrencyCode?: string | null;
  ownerUserId?: string | null; updatedAt: string;
}
interface V2ContactResponse {
  id: string; version: number; accountId?: string | null; givenName: string;
  familyName?: string | null; displayName: string; primaryEmail?: string | null;
  primaryPhone?: string | null; consentSummary: string; lifecycleStatus: string;
  updatedAt: string;
}
interface V2LeadResponse {
  id: string; displayName: string; companyName?: string | null;
  email?: string | null; phone?: string | null; source?: string | null;
  status: string; score?: number | null; updatedAt: string;
}
interface V2OpportunityResponse {
  id: string; accountId: string; contactId?: string | null;
  pipelineId: string; stageId: string; name: string;
  amount?: number | null; currencyCode: string; probability: number;
  status: string; expectedCloseDate?: string | null; updatedAt: string;
}
interface V2ActivityResponse {
  id: string; version?: number; activityType: string; subject: string;
  body?: string | null; relatedType?: string | null; relatedId?: string | null;
  ownerUserId?: string | null; status: string; priority: number;
  startAt?: string | null; dueAt?: string | null; completedAt?: string | null;
  result?: string | null; createdAt?: string | null; updatedAt: string;
}
interface V2PipelineResponse {
  id: string; name: string; currencyCode?: string | null; active: boolean;
}
interface V2StageResponse {
  id: string; pipelineId: string; name: string; sequence: number;
  probability: number; terminalState?: string | null; active: boolean;
}
interface V2TimelineEventResponse {
  id: string; subjectType: string; subjectId: string; eventType: string;
  summary: string; occurredAt: string;
}
interface V2ImportJobResponse {
  id: string; entityType: string; status: string;
  totalRows?: number | null; processedRows?: number | null;
  succeededRows?: number | null; failedRows?: number | null;
  fileName?: string | null; uploadedAt?: string | null;
  startedAt?: string | null; completedAt?: string | null;
  errorMessage?: string | null;
}
interface V2CustomFieldResponse {
  id: string; version: number; entityType: string; fieldKey: string;
  labelAr: string; labelEn: string; dataType: string;
  sensitive: boolean; searchable: boolean; required: boolean;
  active: boolean; createdAt?: string | null; updatedAt?: string | null;
}
/** V2 Customer360Response shape */
interface V2Customer360Response {
  account: V2AccountResponse;
  contacts: V2ContactResponse[];
  opportunities: Array<V2OpportunityResponse & { pipelineName?: string; stageName?: string }>;
  activities: V2ActivityResponse[];
  timeline: V2TimelineEventResponse[];
}
interface V2CustomFieldValuesResponse {
  entityType: string; entityId: string;
  values: Record<string, unknown>;
}

/** V2 Tag response (camelCase, matches backend TagResponse record). */
interface V2TagResponse {
  id: string;
  version: number;
  name: string;
  color?: string | null;
  createdAt: string;
  updatedAt: string;
}

/** V2 Case response (camelCase, matches backend CaseResponse record). */
interface V2CaseResponse {
  id: string;
  version: number;
  subject: string;
  description?: string | null;
  caseType?: string | null;
  status: string;
  priority: number;
  customerId?: string | null;
  assigneeUserId?: string | null;
  ownerUserId?: string | null;
  relatedId?: string | null;
  dueAt?: string | null;
  resolvedAt?: string | null;
  closedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

/** V2 Tag assignment response (camelCase, matches backend TagAssignmentResponse record). */
interface V2TagAssignmentResponse {
  id: string;
  tagId: string;
  tagName: string;
  tagColor?: string | null;
  subjectType: string;
  subjectId: string;
  assignedBy?: string | null;
  assignedAt: string;
}

export const crmApi = {
  dashboard: () => apiClient.get<CrmDashboard>(`${root}/dashboard`, { cache: "no-store" }),

  // ── Accounts (V2 — migrated TD-002-2) ──────────────────────────────────
  accounts: async (search?: string) => {
    const data = await fetchAllPages<V2AccountResponse>((cursor) =>
      apiClient.get<V2ListResponse<V2AccountResponse>>(`${v2root}/accounts`, { query: { limit: 200, search, cursor }, cache: "no-store" }),
    );
    return data.map(mapV2Account);
  },
  createAccount: async (body: { displayName: string; accountType: string; primaryCurrencyCode: string; preferredLocale: string; timeZone: string; source?: string; ownerUserId?: string }) => {
    const data = await unwrapSingle(
      apiClient.post<V2SingleResponse<V2AccountResponse>, typeof body>(`${v2root}/accounts`, body, { context: { headers: { "Idempotency-Key": `account-${Date.now()}-${Math.random().toString(36).slice(2, 10)}` } } }),
    );
    return mapV2Account(data);
  },
  archiveAccount: async (id: string) => {
    // V2 requires If-Match: fetch current ETag first
    const current = await unwrapSingle(apiClient.get<V2SingleResponse<V2AccountResponse>>(`${v2root}/accounts/${id}`, { cache: "no-store" }));
    const data = await unwrapSingle(
      apiClient.patch<V2SingleResponse<{ id: string; version: number; lifecycleStatus: string; updatedAt: string }>, undefined>(
        `${v2root}/accounts/${id}/archive`, undefined, { context: { headers: { "If-Match": "*" } } },
      ),
    );
    return { ...mapV2Account(current), lifecycle_status: data.lifecycleStatus, updated_at: data.updatedAt };
  },
  restoreAccount: async (id: string) => {
    const data = await unwrapSingle(
      apiClient.patch<V2SingleResponse<V2AccountResponse>, undefined>(`${v2root}/accounts/${id}/restore`, undefined, { context: { headers: { "If-Match": "*" } } }),
    );
    return mapV2Account(data);
  },
  customer360: async (id: string) => {
    const res = await apiClient.get<V2SingleResponse<V2Customer360Response>>(`${v2root}/accounts/${id}/customer-360`, { cache: "no-store" });
    const data = res.data;
    return {
      account: mapV2Account(data.account),
      contacts: data.contacts.map((c) => ({
        id: c.id, account_id: c.accountId ?? null, given_name: c.givenName,
        family_name: c.familyName ?? null, display_name: c.displayName, primary_email: c.primaryEmail ?? null,
        primary_phone: c.primaryPhone ?? null, consent_summary: c.consentSummary,
        lifecycle_status: c.lifecycleStatus, updated_at: c.updatedAt,
      })),
      opportunities: data.opportunities.map((o) => ({
        ...mapV2Opportunity(o),
        pipeline_name: (o as V2OpportunityResponse & { pipelineName?: string }).pipelineName,
        stage_name: (o as V2OpportunityResponse & { stageName?: string }).stageName,
      })),
      activities: data.activities.map(mapV2Activity),
      timeline: data.timeline.map(mapV2TimelineEvent),
    } as Customer360;
  },

  // ── Contacts (V2 — migrated TD-002-2) ──────────────────────────────────
  contacts: async (accountId?: string, search?: string) => {
    const data = await fetchAllPages<V2ContactResponse>((cursor) =>
      apiClient.get<V2ListResponse<V2ContactResponse>>(`${v2root}/contacts`, { query: { limit: 200, accountId, search, cursor }, cache: "no-store" }),
    );
    return data.map(mapV2Contact);
  },
  contact: async (id: string) => {
    const data = await unwrapSingle(apiClient.get<V2SingleResponse<V2ContactResponse>>(`${v2root}/contacts/${id}`, { cache: "no-store" }));
    return mapV2Contact(data);
  },
  createContact: async (body: { accountId?: string; givenName: string; familyName?: string; primaryEmail?: string; primaryPhone?: string; preferredLocale: string; timeZone: string; consentSummary: string }) => {
    const data = await unwrapSingle(
      apiClient.post<V2SingleResponse<V2ContactResponse>, typeof body>(`${v2root}/contacts`, body, { context: { headers: { "Idempotency-Key": `contact-${Date.now()}-${Math.random().toString(36).slice(2, 10)}` } } }),
    );
    return mapV2Contact(data);
  },
  archiveContact: async (id: string) => {
    const data = await unwrapSingle(
      apiClient.patch<V2SingleResponse<V2ContactResponse>, undefined>(`${v2root}/contacts/${id}/archive`, undefined, { context: { headers: { "If-Match": "*" } } }),
    );
    return mapV2Contact(data);
  },
  restoreContact: async (id: string) => {
    const data = await unwrapSingle(
      apiClient.patch<V2SingleResponse<V2ContactResponse>, undefined>(`${v2root}/contacts/${id}/restore`, undefined, { context: { headers: { "If-Match": "*" } } }),
    );
    return mapV2Contact(data);
  },

  // ── Leads (V2 — migrated TD-002-2) ─────────────────────────────────────
  leads: async (status?: string) => {
    const data = await fetchAllPages<V2LeadResponse>((cursor) =>
      apiClient.get<V2ListResponse<V2LeadResponse>>(`${v2root}/leads`, { query: { limit: 200, status, cursor }, cache: "no-store" }),
    );
    return data.map(mapV2Lead);
  },
  lead: async (id: string) => {
    const data = await unwrapSingle(apiClient.get<V2SingleResponse<V2LeadResponse>>(`${v2root}/leads/${id}`, { cache: "no-store" }));
    return mapV2Lead(data);
  },
  createLead: async (body: { displayName: string; companyName?: string; email?: string; phone?: string; source?: string; score?: number }) => {
    const data = await unwrapSingle(
      apiClient.post<V2SingleResponse<V2LeadResponse>, typeof body>(`${v2root}/leads`, body, { context: { headers: { "Idempotency-Key": `lead-${Date.now()}-${Math.random().toString(36).slice(2, 10)}` } } }),
    );
    return mapV2Lead(data);
  },
  changeLeadStatus: async (id: string, status: string) => {
    const data = await unwrapSingle(
      apiClient.patch<V2SingleResponse<V2LeadResponse>, { status: string }>(`${v2root}/leads/${id}/status`, { status }, { context: { headers: { "If-Match": "*" } } }),
    );
    return mapV2Lead(data);
  },
  convertLead: async (id: string, body: { createOpportunity: boolean; currencyCode: string; opportunityName?: string; amount?: number; pipelineId?: string; stageId?: string }) => {
    const data = await unwrapSingle(
      apiClient.post<V2SingleResponse<Record<string, unknown>>, typeof body>(`${v2root}/leads/${id}/convert`, body, {
        context: { headers: { "Idempotency-Key": `convert-lead-${id}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}` } },
      }),
    );
    return data;
  },

  // ── Pipelines (V2 read-only — migrated TD-002-2; createPipeline stays V1) ──
  pipelines: async () => {
    const res = await apiClient.get<V2ListResponse<V2PipelineResponse>>(`${v2root}/pipelines`, { cache: "no-store" });
    return (res.data ?? []).map(mapV2Pipeline);
  },
  createPipeline: (body: { name: string; currencyCode: string; stages: string[] }) => apiClient.post<CrmPipeline & { stageIds: string[] }, typeof body>(`${root}/pipelines`, body),
  stages: async (pipelineId: string) => {
    const res = await apiClient.get<V2ListResponse<V2StageResponse>>(`${v2root}/pipelines/${pipelineId}/stages`, { cache: "no-store" });
    return (res.data ?? []).map(mapV2Stage);
  },
  createStage: async (pipelineId: string, body: { name: string; probability?: number; terminalState?: string }) => {
    const data = await unwrapSingle(
      apiClient.post<V2SingleResponse<V2StageResponse>, typeof body>(`${v2root}/pipelines/${pipelineId}/stages`, body),
    );
    return mapV2Stage(data);
  },
  updateStage: async (pipelineId: string, stageId: string, body: { name?: string; probability?: number; terminalState?: string; sequence?: number }) => {
    const data = await unwrapSingle(
      apiClient.patch<V2SingleResponse<V2StageResponse>, typeof body>(`${v2root}/pipelines/${pipelineId}/stages/${stageId}`, body, { context: { headers: { "If-Match": "*" } } }),
    );
    return mapV2Stage(data);
  },
  deleteStage: async (pipelineId: string, stageId: string) => {
    await apiClient.delete<void>(`${v2root}/pipelines/${pipelineId}/stages/${stageId}`);
  },

  // ── Opportunities (V2 — migrated TD-002-2) ─────────────────────────────
  opportunities: async (accountId?: string) => {
    const data = await fetchAllPages<V2OpportunityResponse>((cursor) =>
      apiClient.get<V2ListResponse<V2OpportunityResponse>>(`${v2root}/opportunities`, { query: { limit: 200, accountId, cursor }, cache: "no-store" }),
    );
    return data.map(mapV2Opportunity);
  },
  opportunity: async (id: string) => {
    const data = await unwrapSingle(apiClient.get<V2SingleResponse<V2OpportunityResponse>>(`${v2root}/opportunities/${id}`, { cache: "no-store" }));
    return mapV2Opportunity(data);
  },
  createOpportunity: async (body: { accountId?: string; contactId?: string; pipelineId: string; stageId: string; name: string; amount?: number; currencyCode: string; expectedCloseDate?: string }) => {
    const data = await unwrapSingle(
      apiClient.post<V2SingleResponse<V2OpportunityResponse>, typeof body>(`${v2root}/opportunities`, body, { context: { headers: { "Idempotency-Key": `opp-${Date.now()}-${Math.random().toString(36).slice(2, 10)}` } } }),
    );
    return mapV2Opportunity(data);
  },
  moveOpportunity: async (id: string, stageId: string, reason?: string) => {
    const data = await unwrapSingle(
      apiClient.patch<V2SingleResponse<V2OpportunityResponse>, { stageId: string; reason?: string }>(`${v2root}/opportunities/${id}/stage`, { stageId, reason }, { context: { headers: { "If-Match": "*" } } }),
    );
    return mapV2Opportunity(data);
  },

  // ── Activities (V2 — migrated TD-002-2) ────────────────────────────────
  activities: async (relatedType?: string, relatedId?: string, status?: string) => {
    const data = await fetchAllPages<V2ActivityResponse>((cursor) =>
      apiClient.get<V2ListResponse<V2ActivityResponse>>(`${v2root}/activities`, { query: { limit: 200, relatedType, relatedId, status, cursor }, cache: "no-store" }),
    );
    return data.map(mapV2Activity);
  },
  createActivity: async (body: { activityType: string; subject: string; body?: string; relatedType?: string; relatedId?: string; priority?: number; dueAt?: string; ownerUserId?: string }) => {
    const data = await unwrapSingle(
      apiClient.post<V2SingleResponse<V2ActivityResponse>, typeof body>(`${v2root}/activities`, body, { context: { headers: { "Idempotency-Key": `act-${Date.now()}-${Math.random().toString(36).slice(2, 10)}` } } }),
    );
    return mapV2Activity(data);
  },
  updateActivity: async (id: string, body: { subject?: string; body?: string; priority?: number; startAt?: string; dueAt?: string }) => {
    const data = await unwrapSingle(
      apiClient.patch<V2SingleResponse<V2ActivityResponse>, typeof body>(`${v2root}/activities/${id}`, body, { context: { headers: { "If-Match": "*" } } }),
    );
    return mapV2Activity(data);
  },
  completeActivity: async (id: string, result?: string) => {
    const data = await unwrapSingle(
      apiClient.patch<V2SingleResponse<V2ActivityResponse>, { result?: string }>(`${v2root}/activities/${id}/complete`, { result }, { context: { headers: { "If-Match": "*" } } }),
    );
    return mapV2Activity(data);
  },

  // ── Timeline (V2 — migrated TD-002-2) ──────────────────────────────────
  timeline: async (subjectType: string, subjectId: string) => {
    const res = await apiClient.get<V2ListResponse<V2TimelineEventResponse>>(`${v2root}/timeline/${subjectType}/${subjectId}`, { cache: "no-store" });
    return (res.data ?? []).map(mapV2TimelineEvent);
  },

  // ── Import jobs (V2 — migrated TD-002-2) ───────────────────────────────
  imports: async () => {
    const data = await fetchAllPages<V2ImportJobResponse>((cursor) =>
      apiClient.get<V2ListResponse<V2ImportJobResponse>>(`${v2root}/imports`, { query: { limit: 200, cursor }, cache: "no-store" }),
    );
    return data.map(mapV2ImportJob);
  },
  importJob: async (jobId: string) => {
    const data = await unwrapSingle(apiClient.get<V2SingleResponse<V2ImportJobResponse>>(`${v2root}/imports/${jobId}`, { cache: "no-store" }));
    return mapV2ImportJob(data);
  },
  importJobErrors: async (jobId: string) => {
    const res = await apiClient.get<V2ListResponse<CrmImportErrorRow>>(`${v2root}/imports/${jobId}/errors`, { query: { limit: 500 }, cache: "no-store" });
    return res.data ?? [];
  },
  runImport: async (jobId: string) => {
    const data = await unwrapSingle(
      apiClient.post<V2SingleResponse<V2ImportJobResponse>, undefined>(`${v2root}/imports/${jobId}/run`, undefined, { context: { headers: { "Idempotency-Key": `run-${jobId}-${Date.now()}` } } }),
    );
    return mapV2ImportJob(data);
  },
  cancelImport: async (jobId: string) => {
    const data = await unwrapSingle(apiClient.post<V2SingleResponse<V2ImportJobResponse>, undefined>(`${v2root}/imports/${jobId}/cancel`, undefined));
    return mapV2ImportJob(data);
  },
  importErrorsCsvUrl: (jobId: string) => `${v2root}/imports/${jobId}/errors.csv`,
  /** Fetch the import error log as a Blob (CSV). Uses authenticated fetch. */
  downloadImportErrorsCsv: (jobId: string) => apiClient.getBlob(`${v2root}/imports/${jobId}/errors.csv`, { cache: "no-store" }),
  uploadImport: async (file: File, entityType: string, mapping?: Record<string, unknown>) => {
    const formData = new FormData();
    formData.append("file", file);
    formData.append("entityType", entityType);
    if (mapping) formData.append("mapping", JSON.stringify(mapping));
    const data = await unwrapSingle(
      apiClient.post<V2SingleResponse<V2ImportJobResponse>, FormData>(`${v2root}/imports/upload`, formData, { context: { headers: { "Idempotency-Key": `upload-${Date.now()}-${Math.random().toString(36).slice(2, 10)}` } } }),
    );
    return mapV2ImportJob(data);
  },

  // ── Custom fields (V2 — migrated TD-002-2) ─────────────────────────────
  customFields: async (entityType?: string) => {
    const res = await apiClient.get<V2ListResponse<V2CustomFieldResponse>>(`${v2root}/custom-fields`, { query: { entityType }, cache: "no-store" });
    return (res.data ?? []).map(mapV2CustomField);
  },
  createCustomField: async (body: {
    entityType: string;
    fieldKey: string;
    labelAr: string;
    labelEn: string;
    dataType: string;
    sensitive?: boolean;
    searchable?: boolean;
    required?: boolean;
  }) => {
    const data = await unwrapSingle(
      apiClient.post<V2SingleResponse<V2CustomFieldResponse>, typeof body>(`${v2root}/custom-fields`, body, { context: { headers: { "Idempotency-Key": `cf-${Date.now()}-${Math.random().toString(36).slice(2, 10)}` } } }),
    );
    return mapV2CustomField(data);
  },
  customFieldValues: async (entityType: string, entityId: string) => {
    const response = await unwrapSingle(
      apiClient.get<V2SingleResponse<V2CustomFieldValuesResponse>>(`${v2root}/custom-fields/values/${entityType}/${entityId}`, { cache: "no-store" }),
    );
    return {
      entityType: response.entityType ?? entityType,
      entityId: response.entityId ?? entityId,
      values: Array.isArray(response.values) ? Object.entries(response.values).map(([fieldKey, value]) => ({ fieldKey, value })) : [],
    } satisfies CrmCustomFieldValues;
  },
  upsertCustomFieldValues: async (entityType: string, entityId: string, values: Record<string, unknown>) => {
    const data = await unwrapSingle(
      apiClient.put<V2SingleResponse<V2CustomFieldValuesResponse>, { values: Record<string, unknown> }>(`${v2root}/custom-fields/values/${entityType}/${entityId}`, { values }),
    );
    return {
      entityType: data.entityType ?? entityType,
      entityId: data.entityId ?? entityId,
      values: Array.isArray(data.values) ? Object.entries(data.values).map(([fieldKey, value]) => ({ fieldKey, value })) : [],
    } satisfies CrmCustomFieldValues;
  },

  // ── Tags (CRM.TAG.READ / WRITE) — V2 (TD-006) ──────────────────────────
  tags: async (search?: string) => {
    const data = await fetchAllPages<V2TagResponse>((cursor) =>
      apiClient.get<V2ListResponse<V2TagResponse>>(`${v2root}/tags`, { query: { limit: 200, search, cursor }, cache: "no-store" }),
    );
    return data.map(mapV2Tag);
  },
  tag: async (id: string) => {
    const data = await unwrapSingle(
      apiClient.get<V2SingleResponse<V2TagResponse>>(`${v2root}/tags/${id}`, { cache: "no-store" }),
    );
    return mapV2Tag(data);
  },
  createTag: async (body: { name: string; color?: string }) => {
    const data = await unwrapSingle(
      apiClient.post<V2SingleResponse<V2TagResponse>, typeof body>(`${v2root}/tags`, body, { context: { headers: { "Idempotency-Key": `tag-${Date.now()}-${Math.random().toString(36).slice(2, 10)}` } } }),
    );
    return mapV2Tag(data);
  },
  updateTag: async (id: string, body: { name?: string; color?: string }) => {
    const data = await unwrapSingle(
      apiClient.patch<V2SingleResponse<V2TagResponse>, typeof body>(`${v2root}/tags/${id}`, body, { context: { headers: { "If-Match": "*" } } }),
    );
    return mapV2Tag(data);
  },
  deleteTag: (id: string) => apiClient.delete<void>(`${v2root}/tags/${id}`),
  tagAssignmentsBySubject: async (subjectType: string, subjectId: string) => {
    const data = await fetchAllPages<V2TagAssignmentResponse>((cursor) =>
      apiClient.get<V2ListResponse<V2TagAssignmentResponse>>(`${v2root}/tags/assignments/by-subject`, { query: { subjectType, subjectId, cursor }, cache: "no-store" }),
    );
    return data.map(mapV2TagAssignment);
  },
  assignTag: async (tagId: string, body: { subjectType: string; subjectId: string }) => {
    const data = await unwrapSingle(
      apiClient.post<V2SingleResponse<V2TagAssignmentResponse>, typeof body>(`${v2root}/tags/${tagId}/assignments`, body, { context: { headers: { "Idempotency-Key": `tag-assign-${Date.now()}-${Math.random().toString(36).slice(2, 10)}` } } }),
    );
    return mapV2TagAssignment(data);
  },
  unassignTag: (tagId: string, subjectType: string, subjectId: string) =>
    apiClient.delete<void>(`${v2root}/tags/${tagId}/assignments`, { query: { subjectType, subjectId } }),
  // ── Notes (CRM.NOTE.READ / WRITE) — V1 ONLY (no V2 equivalent; TD-006) ─
  notes: (subjectType: string, subjectId: string, includeArchived?: boolean) =>
    apiClient.get<CrmNote[]>(`${root}/notes`, { query: { subjectType, subjectId, includeArchived: includeArchived ?? false, limit: 200 }, cache: "no-store" }),
  note: (id: string) => apiClient.get<CrmNote>(`${root}/notes/${id}`, { cache: "no-store" }),
  createNote: (body: {
    subjectType: string;
    subjectId: string;
    body: string;
    authorUserId?: string;
  }) => apiClient.post<CrmNote, typeof body>(`${root}/notes`, body),
  archiveNote: (id: string) => apiClient.patch<CrmNote, Record<string, never>>(`${root}/notes/${id}/archive`, {}),
  // ── Tasks (CRM.TASK.READ / WRITE) — V1 ONLY (no V2 equivalent; TD-006) ─
  tasks: (status?: string, assigneeId?: string, relatedId?: string) =>
    apiClient.get<CrmTask[]>(`${root}/tasks`, { query: { limit: 200, status, assigneeId, relatedId }, cache: "no-store" }),
  task: (id: string) => apiClient.get<CrmTask>(`${root}/tasks/${id}`, { cache: "no-store" }),
  createTask: (body: {
    title: string;
    description?: string;
    relatedType?: string;
    relatedId?: string;
    assigneeUserId?: string;
    ownerUserId?: string;
    priority?: number;
    startAt?: string;
    dueAt?: string;
  }) => apiClient.post<CrmTask, typeof body>(`${root}/tasks`, body),
  updateTask: (id: string, body: {
    title?: string;
    description?: string;
    assigneeUserId?: string;
    priority?: number;
    startAt?: string;
    dueAt?: string;
  }) => apiClient.patch<CrmTask, typeof body>(`${root}/tasks/${id}`, body),
  startTask: (id: string) => apiClient.patch<CrmTask, Record<string, never>>(`${root}/tasks/${id}/start`, {}),
  completeTask: (id: string, result?: string) => apiClient.patch<CrmTask, { result?: string }>(`${root}/tasks/${id}/complete`, { result }),
  cancelTask: (id: string, reason?: string) => apiClient.patch<CrmTask, { reason?: string }>(`${root}/tasks/${id}/cancel`, { reason }),

  // ── Cases (CRM.CASE.READ / WRITE) — MOD-001 ────────────────────────────
  cases: async (status?: string, assigneeUserId?: string, customerId?: string) => {
    const data = await fetchAllPages<V2CaseResponse>((cursor) =>
      apiClient.get<V2ListResponse<V2CaseResponse>>(`${v2root}/cases`, { query: { limit: 200, status, assigneeUserId, customerId, cursor }, cache: "no-store" }),
    );
    return data.map(mapV2Case);
  },
  case: async (id: string) => {
    const data = await unwrapSingle(apiClient.get<V2SingleResponse<V2CaseResponse>>(`${v2root}/cases/${id}`, { cache: "no-store" }));
    return mapV2Case(data);
  },
  createCase: (body: {
    subject: string;
    description?: string;
    caseType?: string;
    priority?: number;
    customerId?: string;
    assigneeUserId?: string;
    relatedId?: string;
    dueAt?: string;
  }) => apiClient.post<CrmCase, typeof body>(`/api/v2/crm/cases`, body),
  updateCase: (id: string, body: {
    subject?: string;
    description?: string;
    caseType?: string;
    priority?: number;
    customerId?: string;
    dueAt?: string;
  }) => apiClient.put<CrmCase, typeof body>(`/api/v2/crm/cases/${id}`, body),
  startCase: (id: string) =>
    apiClient.post<CrmCase, Record<string, never>>(`/api/v2/crm/cases/${id}/start`, {}),
  resolveCase: (id: string, resolution?: string) =>
    apiClient.post<CrmCase, { resolution?: string }>(`/api/v2/crm/cases/${id}/resolve`, { resolution }),
  closeCase: (id: string) =>
    apiClient.post<CrmCase, Record<string, never>>(`/api/v2/crm/cases/${id}/close`, {}),
  reopenCase: (id: string) =>
    apiClient.post<CrmCase, Record<string, never>>(`/api/v2/crm/cases/${id}/reopen`, {}),
  assignCase: (id: string, assigneeUserId: string) =>
    apiClient.post<CrmCase, { assigneeUserId: string }>(`/api/v2/crm/cases/${id}/assign`, { assigneeUserId }),

  // ── Transfers (CRM.TRANSFER.READ / REQUEST / APPROVE) — feature/crm-023 ──
  transfers: (state?: string) =>
    apiClient.get<CrmTransfer[]>(`/api/v2/crm/transfers`, { query: { state, pageSize: 200 }, cache: "no-store" }),
  approveTransfer: (id: string, comment?: string) =>
    apiClient.post<CrmTransfer, { decision: string; comment?: string }>(`/api/v2/crm/transfers/${id}/approve`, { decision: "APPROVED", comment }),
  rejectTransfer: (id: string, comment?: string) =>
    apiClient.post<CrmTransfer, { decision: string; comment?: string }>(`/api/v2/crm/transfers/${id}/approve`, { decision: "REJECTED", comment }),

  // ── Teams & Memberships (CRM.TEAM.READ) — feature/crm-023 ──────────────
  teams: () =>
    apiClient.get<CrmTeam[]>(`/api/v2/crm/teams`, { cache: "no-store" }),
  teamMemberships: (teamId: string) =>
    apiClient.get<CrmTeamMembership[]>(`/api/v2/crm/teams/${teamId}/memberships`, { cache: "no-store" }),

  // ── Reports (CRM.ACCOUNT.READ) — V1 ONLY (no V2 equivalent; TD-006) ────
  reports: () => apiClient.get<CrmDashboardReport>(`${root}/reports/dashboard`, { cache: "no-store" }),
  salesPipeline: () => apiClient.get<CrmSalesPipelineReport>(`${root}/reports/sales-pipeline`, { cache: "no-store" }),
  leadConversion: () => apiClient.get<CrmLeadConversionReport>(`${root}/reports/lead-conversion`, { cache: "no-store" }),
  activitySummary: () => apiClient.get<CrmActivitySummaryReport>(`${root}/reports/activity-summary`, { cache: "no-store" }),
  accountGrowth: () => apiClient.get<CrmAccountGrowthReport>(`${root}/reports/account-growth`, { cache: "no-store" }),

  // ── Search (CRM.ACCOUNT.READ) — V1 ONLY (no V2 equivalent; TD-006) ────
  search: (q: string, limit?: number) =>
    apiClient.get<CrmSearchResult[]>(`${root}/search`, { query: { q, limit: limit ?? 20 }, cache: "no-store" }),

  // ── Export (CSV download) — V1 ONLY (no V2 equivalent; TD-006) ────────
  exportAccountsCsvUrl: (search?: string) =>
    `${root}/export/accounts${search ? `?search=${encodeURIComponent(search)}` : ""}`,
  exportContactsCsvUrl: (search?: string) =>
    `${root}/export/contacts${search ? `?search=${encodeURIComponent(search)}` : ""}`,
  exportLeadsCsvUrl: (search?: string) =>
    `${root}/export/leads${search ? `?search=${encodeURIComponent(search)}` : ""}`,
  downloadAccountsCsv: (search?: string) =>
    apiClient.getBlob(`${root}/export/accounts`, { query: { search }, cache: "no-store" }),
  downloadContactsCsv: (search?: string) =>
    apiClient.getBlob(`${root}/export/contacts`, { query: { search }, cache: "no-store" }),
  downloadLeadsCsv: (search?: string) =>
    apiClient.getBlob(`${root}/export/leads`, { query: { search }, cache: "no-store" }),
};

/**
 * CRM Sales Pipeline Report — pipeline velocity by stage.
 * Backend: ReportsController at /api/v1/crm/reports/sales-pipeline
 */
export interface CrmSalesPipelineReport {
  stages: Array<{
    stage_name: string;
    stage_id: string;
    opportunity_count: number;
    total_amount: string;
    avg_probability: string;
  }>;
  total_pipeline_value: string;
  total_opportunities: number;
  weighted_pipeline_value: string;
}

/**
 * CRM Lead Conversion Report — lead funnel and conversion rates.
 * Backend: ReportsController at /api/v1/crm/reports/lead-conversion
 */
export interface CrmLeadConversionReport {
  total_leads: number;
  converted_leads: number;
  qualified_leads: number;
  disqualified_leads: number;
  new_leads: number;
  conversion_rate: number;
  by_source: Array<{
    source: string;
    count: number;
    converted: number;
  }>;
}

/**
 * CRM Activity Summary Report — activity throughput by type.
 * Backend: ReportsController at /api/v1/crm/reports/activity-summary
 */
export interface CrmActivitySummaryReport {
  total_activities: number;
  open_activities: number;
  completed_activities: number;
  total_tasks: number;
  open_tasks: number;
  completed_tasks: number;
  activities_by_type: Array<{
    activity_type: string;
    count: number;
    open_count: number;
  }>;
}

/**
 * CRM Account Growth Report — account growth over time.
 * Backend: ReportsController at /api/v1/crm/reports/account-growth
 */
export interface CrmAccountGrowthReport {
  total_accounts: number;
  active_accounts: number;
  new_this_month: number;
  new_this_quarter: number;
  monthly_growth: Array<{
    month: string;
    new_accounts: number;
    cumulative: number;
  }>;
}

/**
 * CRM Dashboard Report — combined report data.
 * Backend: ReportsController at /api/v1/crm/reports/dashboard
 */
export interface CrmDashboardReport {
  salesPipeline: CrmSalesPipelineReport;
  leadConversion: CrmLeadConversionReport;
  activitySummary: CrmActivitySummaryReport;
  accountGrowth: CrmAccountGrowthReport;
}

/**
 * CRM Transfer Request — ownership transfer between users/teams.
 * Backend: CrmOwnershipTransferController at /api/v2/crm/transfers
 */
export interface CrmTransfer {
  id: string;
  tenantId: string;
  recordType: string;
  recordIds: string[];
  requesterUserId: string;
  currentOwnerUserId: string;
  proposedOwnerUserId?: string | null;
  proposedOwnerTeamId?: string | null;
  transferType: string;
  temporaryEndDate?: string | null;
  reason: string;
  policy: string;
  state: string;
  currentApprovalStep?: number | null;
  executedAt?: string | null;
  executedByUserId?: string | null;
  failureReason?: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * CRM Sales Team — ownership team for record assignment.
 * Backend: CrmOwnershipResourceController at /api/v2/crm/teams
 */
export interface CrmTeam {
  id: string;
  tenantId: string;
  code: string;
  nameAr?: string | null;
  nameEn?: string | null;
  description?: string | null;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * CRM Team Membership — user membership in a sales team.
 * Backend: CrmOwnershipResourceController at /api/v2/crm/teams/{id}/memberships
 */
export interface CrmTeamMembership {
  id: string;
  tenantId: string;
  teamId: string;
  teamCode?: string | null;
  userId: string;
  role: string;
  primary: boolean;
  active: boolean;
  joinedAt: string;
  updatedAt: string;
}

/**
 * CRM cross-entity search result.
 * Branch: feature/crm-search-export
 */
export interface CrmSearchResult {
  entity_type: string;
  entity_id: string;
  display_name: string;
  secondary_info?: string | null;
  matched_field: string;
}
