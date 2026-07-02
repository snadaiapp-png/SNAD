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

const root = "/api/v1/crm";

export const crmApi = {
  dashboard: () => apiClient.get<CrmDashboard>(`${root}/dashboard`, { cache: "no-store" }),
  accounts: (search?: string) => apiClient.get<CrmAccount[]>(`${root}/accounts`, { query: { limit: 200, search }, cache: "no-store" }),
  createAccount: (body: { displayName: string; accountType: string; primaryCurrencyCode: string; preferredLocale: string; timeZone: string; source?: string }) => apiClient.post<CrmAccount, typeof body>(`${root}/accounts`, body),
  archiveAccount: (id: string) => apiClient.patch<CrmAccount>(`${root}/accounts/${id}/archive`),
  restoreAccount: (id: string) => apiClient.patch<CrmAccount>(`${root}/accounts/${id}/restore`),
  customer360: (id: string) => apiClient.get<Customer360>(`${root}/accounts/${id}/customer-360`, { cache: "no-store" }),

  contacts: (accountId?: string, search?: string) => apiClient.get<CrmContact[]>(`${root}/contacts`, { query: { limit: 200, accountId, search }, cache: "no-store" }),
  createContact: (body: { accountId?: string; givenName: string; familyName?: string; primaryEmail?: string; primaryPhone?: string; preferredLocale: string; timeZone: string; consentSummary: string }) => apiClient.post<CrmContact, typeof body>(`${root}/contacts`, body),
  archiveContact: (id: string) => apiClient.patch<CrmContact>(`${root}/contacts/${id}/archive`),
  restoreContact: (id: string) => apiClient.patch<CrmContact>(`${root}/contacts/${id}/restore`),

  leads: (status?: string) => apiClient.get<CrmLead[]>(`${root}/leads`, { query: { limit: 200, status }, cache: "no-store" }),
  createLead: (body: { displayName: string; companyName?: string; email?: string; phone?: string; source?: string; score?: number }) => apiClient.post<CrmLead, typeof body>(`${root}/leads`, body),
  changeLeadStatus: (id: string, status: string) => apiClient.patch<CrmLead, { status: string }>(`${root}/leads/${id}/status`, { status }),
  convertLead: (id: string, body: { createOpportunity: boolean; currencyCode: string; opportunityName?: string; amount?: number }) => apiClient.post<Record<string, unknown>, typeof body>(`${root}/leads/${id}/convert`, body),

  pipelines: () => apiClient.get<CrmPipeline[]>(`${root}/pipelines`, { cache: "no-store" }),
  createPipeline: (body: { name: string; currencyCode: string; stages: string[] }) => apiClient.post<CrmPipeline & { stageIds: string[] }, typeof body>(`${root}/pipelines`, body),
  stages: (pipelineId: string) => apiClient.get<CrmStage[]>(`${root}/pipelines/${pipelineId}/stages`, { cache: "no-store" }),

  opportunities: (accountId?: string) => apiClient.get<CrmOpportunity[]>(`${root}/opportunities`, { query: { limit: 200, accountId }, cache: "no-store" }),
  createOpportunity: (body: { accountId: string; contactId?: string; pipelineId: string; stageId: string; name: string; amount?: number; currencyCode: string; expectedCloseDate?: string }) => apiClient.post<CrmOpportunity, typeof body>(`${root}/opportunities`, body),
  moveOpportunity: (id: string, stageId: string, reason?: string) => apiClient.patch<CrmOpportunity, { stageId: string; reason?: string }>(`${root}/opportunities/${id}/stage`, { stageId, reason }),

  activities: (relatedType?: string, relatedId?: string, status?: string) => apiClient.get<CrmActivity[]>(`${root}/activities`, { query: { limit: 200, relatedType, relatedId, status }, cache: "no-store" }),
  createActivity: (body: { activityType: string; subject: string; body?: string; relatedType?: string; relatedId?: string; priority?: number; dueAt?: string }) => apiClient.post<CrmActivity, typeof body>(`${root}/activities`, body),
  completeActivity: (id: string, result?: string) => apiClient.patch<CrmActivity, { result?: string }>(`${root}/activities/${id}/complete`, { result }),
};
