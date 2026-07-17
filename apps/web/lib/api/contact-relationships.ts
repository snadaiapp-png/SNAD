import { apiClient } from "./client";

const root = "/api/v2/crm";

export interface ApiMeta {
  requestId: string;
  timestamp: string;
}

export interface ApiPage {
  nextCursor?: string | null;
  hasMore: boolean;
  limit: number;
}

export interface ApiSingle<T> {
  data: T;
  meta: ApiMeta;
}

export interface ApiList<T> {
  data: T[];
  page: ApiPage;
  meta: ApiMeta;
}

export type RelationshipRoleCode =
  | "DECISION_MAKER"
  | "BILLING"
  | "TECHNICAL"
  | "INFLUENCER"
  | "EMPLOYEE"
  | "PARTNER"
  | "OTHER";

export type RelationshipStatus = "ACTIVE" | "INACTIVE" | "ARCHIVED";

export type RelationshipCommand =
  | "SET_PRIMARY"
  | "ACTIVATE"
  | "DEACTIVATE"
  | "ARCHIVE"
  | "REACTIVATE";

export interface ContactProfile {
  id: string;
  version: number;
  legalName?: string | null;
  preferredName?: string | null;
  givenName?: string | null;
  middleName?: string | null;
  familyName?: string | null;
  displayName: string;
  primaryEmail?: string | null;
  primaryPhone?: string | null;
  preferredLocale?: string | null;
  timeZone?: string | null;
  pronouns?: string | null;
  lifecycleStatus: string;
  ownerUserId?: string | null;
  source?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ContactRelationship {
  id: string;
  version: number;
  contactId: string;
  accountId: string;
  contactDisplayName: string;
  accountDisplayName: string;
  roleCode: RelationshipRoleCode;
  customRoleId?: string | null;
  customRoleNameAr?: string | null;
  customRoleNameEn?: string | null;
  status: RelationshipStatus;
  primaryRelationship: boolean;
  validFrom?: string | null;
  validTo?: string | null;
  jobTitle?: string | null;
  department?: string | null;
  decisionAuthority: string;
  ownerUserId?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface RelationshipRole {
  id?: string | null;
  version: number;
  code: string;
  nameAr: string;
  nameEn: string;
  standard: boolean;
  active: boolean;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface RelationshipHistory {
  id: string;
  relationshipId: string;
  contactId: string;
  accountId: string;
  eventType: string;
  previousVersion?: number | null;
  newVersion: number;
  snapshot: string;
  changedBy: string;
  changedAt: string;
}

export interface OwnershipHistory {
  id: string;
  contactId: string;
  previousOwnerUserId?: string | null;
  newOwnerUserId?: string | null;
  changedBy: string;
  changedAt: string;
  reason?: string | null;
}

export interface CreateRelationshipInput {
  accountId: string;
  roleCode: RelationshipRoleCode;
  customRoleId?: string | null;
  primaryRelationship: boolean;
  validFrom?: string | null;
  validTo?: string | null;
  jobTitle?: string | null;
  department?: string | null;
  decisionAuthority?: string | null;
  ownerUserId?: string | null;
}

export interface UpdateRelationshipInput {
  expectedVersion: number;
  roleCode?: RelationshipRoleCode | null;
  customRoleId?: string | null;
  validFrom?: string | null;
  validTo?: string | null;
  jobTitle?: string | null;
  department?: string | null;
  decisionAuthority?: string | null;
  ownerUserId?: string | null;
}

export interface UpdateContactProfileInput {
  expectedVersion: number;
  legalName?: string | null;
  preferredName?: string | null;
  givenName?: string | null;
  middleName?: string | null;
  familyName?: string | null;
  primaryEmail?: string | null;
  primaryPhone?: string | null;
  preferredLocale?: string | null;
  timeZone?: string | null;
  pronouns?: string | null;
  ownerUserId?: string | null;
  source?: string | null;
  ownerChangeReason?: string | null;
}

export const contactRelationshipApi = {
  profile: async (contactId: string): Promise<ContactProfile> => {
    const response = await apiClient.get<ApiSingle<ContactProfile>>(
      `${root}/contacts/${contactId}/profile`,
      { cache: "no-store" },
    );
    return response.data;
  },

  updateProfile: async (
    contactId: string,
    input: UpdateContactProfileInput,
  ): Promise<ContactProfile> => {
    const response = await apiClient.patch<ApiSingle<ContactProfile>, UpdateContactProfileInput>(
      `${root}/contacts/${contactId}/profile-versioned`,
      input,
    );
    return response.data;
  },

  byContact: async (
    contactId: string,
    cursor?: string,
  ): Promise<ApiList<ContactRelationship>> =>
    apiClient.get<ApiList<ContactRelationship>>(
      `${root}/contacts/${contactId}/relationships`,
      { query: { limit: 200, cursor }, cache: "no-store" },
    ),

  byAccount: async (
    accountId: string,
    cursor?: string,
  ): Promise<ApiList<ContactRelationship>> =>
    apiClient.get<ApiList<ContactRelationship>>(
      `${root}/accounts/${accountId}/contact-relationships`,
      { query: { limit: 200, cursor }, cache: "no-store" },
    ),

  create: async (
    contactId: string,
    input: CreateRelationshipInput,
  ): Promise<ContactRelationship> => {
    const response = await apiClient.post<ApiSingle<ContactRelationship>, CreateRelationshipInput>(
      `${root}/contacts/${contactId}/relationships`,
      input,
    );
    return response.data;
  },

  update: async (
    relationshipId: string,
    input: UpdateRelationshipInput,
  ): Promise<ContactRelationship> => {
    const response = await apiClient.patch<ApiSingle<ContactRelationship>, UpdateRelationshipInput>(
      `${root}/contact-relationships/${relationshipId}/versioned`,
      input,
    );
    return response.data;
  },

  command: async (
    relationshipId: string,
    expectedVersion: number,
    action: RelationshipCommand,
  ): Promise<ContactRelationship> => {
    const response = await apiClient.post<
      ApiSingle<ContactRelationship>,
      { expectedVersion: number; action: RelationshipCommand }
    >(`${root}/contact-relationships/${relationshipId}/commands`, {
      expectedVersion,
      action,
    });
    return response.data;
  },

  roles: async (): Promise<RelationshipRole[]> => {
    const response = await apiClient.get<ApiList<RelationshipRole>>(
      `${root}/relationship-roles`,
      { cache: "no-store" },
    );
    return response.data;
  },

  createRole: async (input: {
    code: string;
    nameAr: string;
    nameEn: string;
  }): Promise<RelationshipRole> => {
    const response = await apiClient.post<
      ApiSingle<RelationshipRole>,
      { code: string; nameAr: string; nameEn: string }
    >(`${root}/relationship-roles`, input);
    return response.data;
  },

  history: async (relationshipId: string): Promise<RelationshipHistory[]> => {
    const response = await apiClient.get<ApiList<RelationshipHistory>>(
      `${root}/contact-relationships/${relationshipId}/history`,
      { query: { limit: 200 }, cache: "no-store" },
    );
    return response.data;
  },

  ownershipHistory: async (contactId: string): Promise<OwnershipHistory[]> => {
    const response = await apiClient.get<ApiList<OwnershipHistory>>(
      `${root}/contacts/${contactId}/ownership-history`,
      { query: { limit: 200 }, cache: "no-store" },
    );
    return response.data;
  },
};
