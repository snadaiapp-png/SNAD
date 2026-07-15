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
  activity_type: string;
  subject: string;
  body?: string | null;
  related_type?: string | null;
  related_id?: string | null;
  status: string;
  priority: number;
  due_at?: string | null;
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
 * CRM import job — backend representation.
 * Field names mirror the JSON keys returned by /api/v1/crm/imports.
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
 * CRM custom field definition — backend representation.
 * Field names mirror the JSON keys returned by /api/v1/crm/custom-fields.
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

const root = "/api/v1/crm";

export const crmApi = {
  dashboard: () => apiClient.get<CrmDashboard>(`${root}/dashboard`, { cache: "no-store" }),
  accounts: (search?: string) => apiClient.get<CrmAccount[]>(`${root}/accounts`, { query: { limit: 200, search }, cache: "no-store" }),
  createAccount: (body: { displayName: string; accountType: string; primaryCurrencyCode: string; preferredLocale: string; timeZone: string; source?: string }) => apiClient.post<CrmAccount, typeof body>(`${root}/accounts`, body),
  archiveAccount: (id: string) => apiClient.patch<CrmAccount>(`${root}/accounts/${id}/archive`),
  restoreAccount: (id: string) => apiClient.patch<CrmAccount>(`${root}/accounts/${id}/restore`),
  customer360: (id: string) => apiClient.get<Customer360>(`${root}/accounts/${id}/customer-360`, { cache: "no-store" }),

  contacts: (accountId?: string, search?: string) => apiClient.get<CrmContact[]>(`${root}/contacts`, { query: { limit: 200, accountId, search }, cache: "no-store" }),
  contact: (id: string) => apiClient.get<CrmContact>(`${root}/contacts/${id}`, { cache: "no-store" }),
  createContact: (body: { accountId?: string; givenName: string; familyName?: string; primaryEmail?: string; primaryPhone?: string; preferredLocale: string; timeZone: string; consentSummary: string }) => apiClient.post<CrmContact, typeof body>(`${root}/contacts`, body),
  archiveContact: (id: string) => apiClient.patch<CrmContact>(`${root}/contacts/${id}/archive`),
  restoreContact: (id: string) => apiClient.patch<CrmContact>(`${root}/contacts/${id}/restore`),

  leads: (status?: string) => apiClient.get<CrmLead[]>(`${root}/leads`, { query: { limit: 200, status }, cache: "no-store" }),
  lead: (id: string) => apiClient.get<CrmLead>(`${root}/leads/${id}`, { cache: "no-store" }),
  createLead: (body: { displayName: string; companyName?: string; email?: string; phone?: string; source?: string; score?: number }) => apiClient.post<CrmLead, typeof body>(`${root}/leads`, body),
  changeLeadStatus: (id: string, status: string) => apiClient.patch<CrmLead, { status: string }>(`${root}/leads/${id}/status`, { status }),
  convertLead: (id: string, body: { createOpportunity: boolean; currencyCode: string; opportunityName?: string; amount?: number; pipelineId?: string; stageId?: string }) => apiClient.post<Record<string, unknown>, typeof body>(`${root}/leads/${id}/convert`, body),

  pipelines: () => apiClient.get<CrmPipeline[]>(`${root}/pipelines`, { cache: "no-store" }),
  createPipeline: (body: { name: string; currencyCode: string; stages: string[] }) => apiClient.post<CrmPipeline & { stageIds: string[] }, typeof body>(`${root}/pipelines`, body),
  stages: (pipelineId: string) => apiClient.get<CrmStage[]>(`${root}/pipelines/${pipelineId}/stages`, { cache: "no-store" }),

  opportunities: (accountId?: string) => apiClient.get<CrmOpportunity[]>(`${root}/opportunities`, { query: { limit: 200, accountId }, cache: "no-store" }),
  opportunity: (id: string) => apiClient.get<CrmOpportunity>(`${root}/opportunities/${id}`, { cache: "no-store" }),
  createOpportunity: (body: { accountId: string; contactId?: string; pipelineId: string; stageId: string; name: string; amount?: number; currencyCode: string; expectedCloseDate?: string }) => apiClient.post<CrmOpportunity, typeof body>(`${root}/opportunities`, body),
  moveOpportunity: (id: string, stageId: string, reason?: string) => apiClient.patch<CrmOpportunity, { stageId: string; reason?: string }>(`${root}/opportunities/${id}/stage`, { stageId, reason }),

  activities: (relatedType?: string, relatedId?: string, status?: string) => apiClient.get<CrmActivity[]>(`${root}/activities`, { query: { limit: 200, relatedType, relatedId, status }, cache: "no-store" }),
  createActivity: (body: { activityType: string; subject: string; body?: string; relatedType?: string; relatedId?: string; priority?: number; dueAt?: string }) => apiClient.post<CrmActivity, typeof body>(`${root}/activities`, body),
  completeActivity: (id: string, result?: string) => apiClient.patch<CrmActivity, { result?: string }>(`${root}/activities/${id}/complete`, { result }),

  // ── Timeline (CRM.TIMELINE.READ) ──────────────────────────────────────
  timeline: (subjectType: string, subjectId: string) => apiClient.get<CrmTimelineEvent[]>(`${root}/timeline/${subjectType}/${subjectId}`, { cache: "no-store" }),

  // ── Import jobs (CRM.IMPORT.READ / WRITE) ──────────────────────────────
  imports: () => apiClient.get<CrmImportJob[]>(`${root}/imports`, { query: { limit: 200 }, cache: "no-store" }),
  importJob: (jobId: string) => apiClient.get<CrmImportJob>(`${root}/imports/${jobId}`, { cache: "no-store" }),
  importJobErrors: (jobId: string) => apiClient.get<CrmImportErrorRow[]>(`${root}/imports/${jobId}/errors`, { query: { limit: 500 }, cache: "no-store" }),
  runImport: (jobId: string) => apiClient.post<CrmImportJob, undefined>(`${root}/imports/${jobId}/run`, undefined),
  cancelImport: (jobId: string) => apiClient.post<CrmImportJob, undefined>(`${root}/imports/${jobId}/cancel`, undefined),
  importErrorsCsvUrl: (jobId: string) => `${root}/imports/${jobId}/errors.csv`,
  /** Fetch the import error log as a Blob (CSV). Uses authenticated fetch. */
  downloadImportErrorsCsv: (jobId: string) => apiClient.getBlob(`${root}/imports/${jobId}/errors.csv`, { cache: "no-store" }),
  uploadImport: (file: File, entityType: string, mapping?: Record<string, unknown>) => {
    const formData = new FormData();
    formData.append("file", file);
    formData.append("entityType", entityType);
    if (mapping) formData.append("mapping", JSON.stringify(mapping));
    // Do NOT set Content-Type manually — the browser sets the multipart
    // boundary automatically when the body is a FormData instance.
    return apiClient.post<CrmImportJob, FormData>(`${root}/imports/upload`, formData);
  },

  // ── Custom fields (CRM.CUSTOM_FIELD.READ / WRITE) ──────────────────────
  customFields: (entityType?: string) => apiClient.get<CrmCustomField[]>(`${root}/custom-fields`, { query: { entityType }, cache: "no-store" }),
  createCustomField: (body: {
    entityType: string;
    fieldKey: string;
    labelAr: string;
    labelEn: string;
    dataType: string;
    sensitive?: boolean;
    searchable?: boolean;
    required?: boolean;
  }) => apiClient.post<CrmCustomField, typeof body>(`${root}/custom-fields`, body),
  customFieldValues: async (entityType: string, entityId: string) => {
    const response = await apiClient.get<Partial<CrmCustomFieldValues>>(
      `${root}/custom-fields/values/${entityType}/${entityId}`,
      { cache: "no-store" },
    );
    return {
      entityType: response.entityType ?? entityType,
      entityId: response.entityId ?? entityId,
      values: Array.isArray(response.values) ? response.values : [],
    } satisfies CrmCustomFieldValues;
  },
  upsertCustomFieldValues: (entityType: string, entityId: string, values: Record<string, unknown>) => apiClient.put<CrmCustomFieldValues, { values: Record<string, unknown> }>(`${root}/custom-fields/values/${entityType}/${entityId}`, { values }),

  // ── Tags (CRM.TAG.READ / WRITE) — feature/crm-tags ───────────────────
  tags: (search?: string) =>
    apiClient.get<CrmTag[]>(`${root}/tags`, { query: { limit: 200, search }, cache: "no-store" }),
  tag: (id: string) => apiClient.get<CrmTag>(`${root}/tags/${id}`, { cache: "no-store" }),
  createTag: (body: { name: string; color?: string }) =>
    apiClient.post<CrmTag, typeof body>(`${root}/tags`, body),
  updateTag: (id: string, body: { name?: string; color?: string }) =>
    apiClient.patch<CrmTag, typeof body>(`${root}/tags/${id}`, body),
  deleteTag: (id: string) => apiClient.delete<void>(`${root}/tags/${id}`),
  tagAssignmentsBySubject: (subjectType: string, subjectId: string) =>
    apiClient.get<CrmTagAssignment[]>(`${root}/tags/assignments/by-subject`, { query: { subjectType, subjectId }, cache: "no-store" }),
  assignTag: (tagId: string, body: { subjectType: string; subjectId: string }) =>
    apiClient.post<CrmTagAssignment, typeof body>(`${root}/tags/${tagId}/assignments`, body),
  unassignTag: (tagId: string, subjectType: string, subjectId: string) =>
    apiClient.delete<void>(`${root}/tags/${tagId}/assignments`, { query: { subjectType, subjectId } }),
};
