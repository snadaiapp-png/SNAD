import { apiClient } from "./client";

const root = "/api/v1/access";

export type OverrideEffect = "ALLOW" | "DENY";

export interface PermissionOverride {
  id: string;
  userId: string;
  capabilityCode: string;
  effect: OverrideEffect;
  scopeType: string | null;
  scopeReference: string | null;
  reason: string;
  validFrom: string;
  validUntil: string | null;
  version: number;
}

export interface CreateOverrideInput {
  targetUserId: string;
  capabilityCode: string;
  effect: OverrideEffect;
  scopeType: string | null;
  scopeReference: string | null;
  reason: string;
  validFrom: string | null;
  validUntil: string | null;
}

export interface SubjectRelationship {
  id: string;
  subjectUserId: string;
  relationshipType: string;
  objectType: string;
  objectId: string;
  validFrom: string;
  validUntil: string | null;
  source: string;
}

export interface EffectivePermission {
  capabilityId: string;
  effect: "ALLOW" | "DENY";
  scopeType: string;
  scopeReference: string | null;
  source: "ROLE" | "OVERRIDE" | "BREAK_GLASS";
  reason: string;
  matchedRoleId: string | null;
  authorizationVersion: number;
  computedAt: string;
}

export const listOverrides = (userId: string): Promise<PermissionOverride[]> =>
  apiClient.get<PermissionOverride[]>(`${root}/overrides`, {
    query: { userId },
    cache: "no-store",
  });

export const createOverride = (body: CreateOverrideInput): Promise<PermissionOverride> =>
  apiClient.post<PermissionOverride, CreateOverrideInput>(`${root}/overrides`, body);

export const revokeOverride = (overrideId: string): Promise<void> =>
  apiClient.patch<void, undefined>(`${root}/overrides/${overrideId}/revoke`, undefined);

export const listRelationships = (userId: string): Promise<SubjectRelationship[]> =>
  apiClient.get<SubjectRelationship[]>(`${root}/relationships`, {
    query: { userId },
    cache: "no-store",
  });

export interface CreateRelationshipInput {
  subjectUserId: string;
  relationshipType: string;
  objectType: string;
  objectId: string;
  validFrom?: string | null;
  validUntil?: string | null;
  reason?: string | null;
}

export const createRelationship = (body: CreateRelationshipInput): Promise<SubjectRelationship> =>
  apiClient.post<SubjectRelationship, CreateRelationshipInput>(`${root}/relationships`, body);

export const revokeRelationship = (relationshipId: string, reason?: string): Promise<void> =>
  apiClient.patch<void, undefined>(`${root}/relationships/${relationshipId}/revoke`, undefined, {
    query: reason ? { reason } : undefined,
  });

export const effectivePermissions = (userId: string): Promise<EffectivePermission[]> =>
  apiClient.get<EffectivePermission[]>(`${root}/effective-permissions`, {
    query: { userId },
    cache: "no-store",
  });

export const resync = (userId: string): Promise<EffectivePermission[]> =>
  apiClient.post<EffectivePermission[], undefined>(`${root}/effective-permissions/resync`, undefined, {
    query: { userId },
  });
