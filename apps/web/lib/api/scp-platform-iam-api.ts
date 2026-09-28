import { apiClient } from "./client";

const root = "/api/v1/executive";

export type PlatformMembershipStatus = "INVITED" | "ACTIVE" | "SUSPENDED" | "LOCKED" | "DISABLED";
export type PlatformUserLifecycle = "activate" | "suspend" | "lock" | "disable";

export interface PlatformUser {
  userId: string;
  email: string;
  displayName: string | null;
  accountStatus: string;
  membershipStatus: PlatformMembershipStatus;
  lastLoginAt: string | null;
  joinedAt: string;
}

export interface CreatePlatformUserInput {
  email: string;
  displayName?: string;
}

export interface UpdatePlatformUserInput {
  email?: string;
  displayName?: string;
}

export interface PlatformUserRoleGrant {
  id: string;
  tenantId: string;
  userId: string;
  roleId: string;
  roleCode: string;
  organizationId: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface PlatformSessionSummary {
  userId: string;
  sessionVersion: number;
  lastLoginAt: string | null;
}

export interface PlatformRole {
  id: string;
  code: string;
  name: string;
  description: string | null;
  status: string;
  roleType: "SYSTEM" | "CUSTOM";
  protectedRole: boolean;
  ownerRole: boolean;
}

export interface PlatformCapability {
  id: string;
  code: string;
  name: string;
  description: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface PlatformRoleCapability {
  id: string;
  tenantId: string;
  roleId: string;
  capabilityId: string;
  capabilityCode: string;
  createdAt: string;
}

export interface CreatePlatformRoleInput {
  code: string;
  name: string;
  description?: string;
}

export interface UpdatePlatformRoleInput {
  name: string;
  description?: string;
}

export const scpApi = {
  platformUsers: () => apiClient.get<PlatformUser[]>(`${root}/users`),
  platformUser: (userId: string) => apiClient.get<PlatformUser>(`${root}/users/${userId}`),
  createPlatformUser: (body: CreatePlatformUserInput) =>
    apiClient.post<PlatformUser, CreatePlatformUserInput>(`${root}/users`, body),
  updatePlatformUser: (userId: string, body: UpdatePlatformUserInput) =>
    apiClient.patch<PlatformUser, UpdatePlatformUserInput>(`${root}/users/${userId}`, body),
  transitionPlatformUser: (userId: string, transition: PlatformUserLifecycle, reason?: string) =>
    apiClient.post<PlatformUser, { reason?: string }>(`${root}/users/${userId}/${transition}`, { reason }),
  platformUserRoles: (userId: string) =>
    apiClient.get<PlatformUserRoleGrant[]>(`${root}/users/${userId}/roles`),
  replacePlatformUserRoles: (userId: string, roleIds: string[], reason: string) =>
    apiClient.put<PlatformUserRoleGrant[], { roleIds: string[]; reason: string }>(
      `${root}/users/${userId}/roles`, { roleIds, reason },
    ),
  platformUserPermissions: (userId: string) =>
    apiClient.get<string[]>(`${root}/users/${userId}/permissions`),
  platformUserSessions: (userId: string) =>
    apiClient.get<PlatformSessionSummary>(`${root}/users/${userId}/sessions`),
  revokePlatformUserSessions: (userId: string, reason: string) =>
    apiClient.post<PlatformSessionSummary, { reason: string }>(
      `${root}/users/${userId}/sessions/revoke`, { reason },
    ),
  platformRoles: () => apiClient.get<PlatformRole[]>(`${root}/roles`),
  platformRole: (roleId: string) => apiClient.get<PlatformRole>(`${root}/roles/${roleId}`),
  createPlatformRole: (body: CreatePlatformRoleInput) =>
    apiClient.post<PlatformRole, CreatePlatformRoleInput>(`${root}/roles`, body),
  updatePlatformRole: (roleId: string, body: UpdatePlatformRoleInput) =>
    apiClient.patch<PlatformRole, UpdatePlatformRoleInput>(`${root}/roles/${roleId}`, body),
  platformCapabilities: () => apiClient.get<PlatformCapability[]>(`${root}/capabilities`),
  platformRoleCapabilities: (roleId: string) =>
    apiClient.get<PlatformRoleCapability[]>(`${root}/roles/${roleId}/capabilities`),
  replacePlatformRoleCapabilities: (roleId: string, capabilityIds: string[]) =>
    apiClient.put<PlatformRoleCapability[], { capabilityIds: string[] }>(
      `${root}/roles/${roleId}/capabilities`, { capabilityIds },
    ),
};
