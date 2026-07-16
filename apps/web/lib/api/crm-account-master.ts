import { apiClient } from "./client";

const root = "/api/v2/crm/accounts";
const taxonomyRoot = "/api/v2/crm/account-taxonomies";

export interface AccountMasterProfile {
  accountId: string;
  version: number;
  legalName: string;
  tradeName?: string | null;
  registrationNumber?: string | null;
  taxRegistrationNumber?: string | null;
  industry?: string | null;
  organizationSize?: "MICRO" | "SMALL" | "MEDIUM" | "LARGE" | "ENTERPRISE" | null;
  websiteUrl?: string | null;
  customerTier?: string | null;
  classificationId?: string | null;
  segmentId?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AccountMasterRisk {
  accountId: string;
  version: number;
  riskLevel: "UNKNOWN" | "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  riskFlags: string[];
  mergeCandidate: boolean;
  updatedAt: string;
}

export interface AccountProjection {
  id?: string | null;
  projectionType: "FINANCIAL_SUMMARY" | "ORDERS" | "SERVICE";
  sourceSystem: string;
  connectionStatus: "READY" | "STALE" | "NOT_CONNECTED" | "ERROR";
  payload?: unknown;
  sourceUpdatedAt?: string | null;
  syncedAt?: string | null;
}

export interface AccountMasterOverview {
  accountId: string;
  accountVersion: number;
  displayName: string;
  accountType: string;
  lifecycleStatus: string;
  ownerUserId?: string | null;
  profile: AccountMasterProfile;
  projections: AccountProjection[];
}

export interface AccountRelationship {
  id: string;
  version: number;
  sourceAccountId: string;
  targetAccountId: string;
  relationshipType: "PARENT" | "SUBSIDIARY" | "BRANCH" | "PARTNER";
  status: "ACTIVE" | "ENDED";
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
  description?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AccountExternalIdentifier {
  id: string;
  accountId: string;
  provider: string;
  systemScope: string;
  externalId: string;
  label?: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AccountStatusHistory {
  id: string;
  fromStatus?: string | null;
  toStatus: string;
  reason?: string | null;
  changedBy: string;
  changedAt: string;
}

export interface AccountOwnershipHistory {
  id: string;
  fromOwnerUserId?: string | null;
  toOwnerUserId?: string | null;
  reason?: string | null;
  changedBy: string;
  changedAt: string;
}

export interface AccountHistory {
  statusHistory: AccountStatusHistory[];
  ownershipHistory: AccountOwnershipHistory[];
}

export interface AccountTaxonomy {
  id: string;
  version: number;
  taxonomyType: "CLASSIFICATION" | "SEGMENT";
  code: string;
  nameAr: string;
  nameEn: string;
  parentId?: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

async function strongEtag(entityType: string, id: string, version: number): Promise<string> {
  const material = `${entityType.toLowerCase()}:${id}:${version}`;
  const bytes = new TextEncoder().encode(material);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  const hex = Array.from(new Uint8Array(digest).slice(0, 8))
    .map((value) => value.toString(16).padStart(2, "0"))
    .join("");
  return `"${entityType.toLowerCase()}-${id}-v${version}-${hex}"`;
}

export const crmAccountMasterApi = {
  overview: (accountId: string) =>
    apiClient.get<AccountMasterOverview>(`${root}/${accountId}/master`, { cache: "no-store" }),

  risk: (accountId: string) =>
    apiClient.get<AccountMasterRisk>(`${root}/${accountId}/master/risk`, { cache: "no-store" }),

  updateProfile: async (
    accountId: string,
    version: number,
    body: {
      legalName?: string;
      tradeName?: string;
      registrationNumber?: string;
      taxRegistrationNumber?: string;
      industry?: string;
      organizationSize?: string;
      websiteUrl?: string;
      customerTier?: string;
      classificationId?: string;
      segmentId?: string;
    },
  ) => apiClient.put<AccountMasterProfile, typeof body>(
    `${root}/${accountId}/master/profile`,
    body,
    { context: { headers: { "If-Match": await strongEtag("account-master", accountId, version) } } },
  ),

  updateRisk: async (
    accountId: string,
    version: number,
    body: { riskLevel?: string; riskFlags?: string[]; mergeCandidate?: boolean },
  ) => apiClient.put<AccountMasterRisk, typeof body>(
    `${root}/${accountId}/master/risk`,
    body,
    { context: { headers: { "If-Match": await strongEtag("account-master", accountId, version) } } },
  ),

  relationships: (accountId: string) =>
    apiClient.get<AccountRelationship[]>(`${root}/${accountId}/relationships`, { cache: "no-store" }),

  createRelationship: (
    accountId: string,
    body: {
      targetAccountId: string;
      relationshipType: string;
      effectiveFrom?: string;
      effectiveTo?: string;
      description?: string;
    },
  ) => apiClient.post<AccountRelationship, typeof body>(`${root}/${accountId}/relationships`, body),

  endRelationship: async (accountId: string, relationship: AccountRelationship) =>
    apiClient.patch<AccountRelationship, { effectiveTo: string }>(
      `${root}/${accountId}/relationships/${relationship.id}/end`,
      { effectiveTo: new Date().toISOString().slice(0, 10) },
      { context: { headers: { "If-Match": await strongEtag("account-relationship", relationship.id, relationship.version) } } },
    ),

  externalIdentifiers: (accountId: string) =>
    apiClient.get<AccountExternalIdentifier[]>(`${root}/${accountId}/external-identifiers`, { cache: "no-store" }),

  createExternalIdentifier: (
    accountId: string,
    body: { provider: string; systemScope: string; externalId: string; label?: string },
  ) => apiClient.post<AccountExternalIdentifier, typeof body>(`${root}/${accountId}/external-identifiers`, body),

  removeExternalIdentifier: (accountId: string, identifierId: string) =>
    apiClient.delete<void>(`${root}/${accountId}/external-identifiers/${identifierId}`),

  history: (accountId: string) =>
    apiClient.get<AccountHistory>(`${root}/${accountId}/history`, { cache: "no-store" }),

  projections: (accountId: string) =>
    apiClient.get<AccountProjection[]>(`${root}/${accountId}/projections`, { cache: "no-store" }),

  taxonomies: (type: "CLASSIFICATION" | "SEGMENT") =>
    apiClient.get<AccountTaxonomy[]>(taxonomyRoot, { query: { type }, cache: "no-store" }),
};
