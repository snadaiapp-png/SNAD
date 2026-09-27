import { apiClient, ApiClient } from "./client";
import { requireValidUuid } from "./validation";
import type { OrganizationMembershipResponse } from "./memberships";

export type RoleStatus = "ACTIVE" | "INACTIVE" | "ARCHIVED";
export type CapabilityStatus = "ACTIVE" | "INACTIVE";
export type UserRoleGrantStatus = "ACTIVE" | "REVOKED" | string;

export interface RoleResponse {
  id: string;
  tenantId: string;
  code: string;
  name: string;
  description: string | null;
  status: RoleStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRoleRequest {
  code: string;
  name: string;
  description?: string | null;
}

export interface UpdateRoleRequest {
  name: string;
  description?: string | null;
}

export interface CapabilityResponse {
  id: string;
  code: string;
  name: string;
  description: string | null;
  status: CapabilityStatus;
  createdAt: string;
  updatedAt: string;
}

export interface RoleAccessResponse {
  id: string;
  tenantId: string;
  roleId: string;
  capabilityId: string;
  capabilityCode: string;
  createdAt: string;
}

export interface UserRoleLinkResponse {
  id: string;
  tenantId: string;
  userId: string;
  roleId: string;
  roleCode: string;
  organizationId: string | null;
  status: UserRoleGrantStatus;
  createdAt: string;
  updatedAt: string;
}

export type RoleLifecycleAction = "activate" | "deactivate" | "archive";
export type CapabilityLifecycleAction = "activate" | "deactivate";

export function createTenantAccessApi(client: ApiClient = apiClient) {
  return {
    async listUserMemberships(userId: string) {
      return client.get<OrganizationMembershipResponse[]>(
        `/api/v1/users/${requireValidUuid(userId, "userId")}/memberships`,
        undefined,
      );
    },

    async listRoles(tenantId: string) {
      return client.get<RoleResponse[]>("/api/v1/access/roles", {
        query: { tenantId: requireValidUuid(tenantId, "tenantId") },
      });
    },

    async getRole(tenantId: string, roleId: string) {
      return client.get<RoleResponse>(
        `/api/v1/access/roles/${requireValidUuid(roleId, "roleId")}`,
        { query: { tenantId: requireValidUuid(tenantId, "tenantId") } },
      );
    },

    async listCapabilities() {
      return client.get<CapabilityResponse[]>("/api/v1/access/capabilities", undefined);
    },

    async listRoleCapabilities(tenantId: string, roleId: string) {
      return client.get<RoleAccessResponse[]>(
        `/api/v1/access/roles/${requireValidUuid(roleId, "roleId")}/access-items`,
        { query: { tenantId: requireValidUuid(tenantId, "tenantId") } },
      );
    },

    async attachRoleCapability(tenantId: string, roleId: string, capabilityId: string) {
      return client.post<RoleAccessResponse>(
        `/api/v1/access/roles/${requireValidUuid(roleId, "roleId")}/access-items/${requireValidUuid(capabilityId, "capabilityId")}`,
        undefined,
        { query: { tenantId: requireValidUuid(tenantId, "tenantId") } },
      );
    },

    async detachRoleCapability(tenantId: string, roleId: string, capabilityId: string) {
      return client.delete<void>(
        `/api/v1/access/roles/${requireValidUuid(roleId, "roleId")}/access-items/${requireValidUuid(capabilityId, "capabilityId")}`,
        { query: { tenantId: requireValidUuid(tenantId, "tenantId") } },
      );
    },

    async listUserRoleLinks(tenantId: string, userId: string) {
      return client.get<UserRoleLinkResponse[]>(
        `/api/v1/access/users/${requireValidUuid(userId, "userId")}/role-links`,
        { query: { tenantId: requireValidUuid(tenantId, "tenantId") } },
      );
    },

    async grantUserRole(
      tenantId: string,
      userId: string,
      roleId: string,
      organizationId?: string,
    ) {
      const query: Record<string, string> = {
        tenantId: requireValidUuid(tenantId, "tenantId"),
      };
      if (organizationId !== undefined) {
        query.organizationId = requireValidUuid(organizationId, "organizationId");
      }
      return client.post<UserRoleLinkResponse>(
        `/api/v1/access/users/${requireValidUuid(userId, "userId")}/role-links/${requireValidUuid(roleId, "roleId")}`,
        undefined,
        { query },
      );
    },

    async revokeUserRole(tenantId: string, grantId: string) {
      return client.patch<UserRoleLinkResponse>(
        `/api/v1/access/users/role-links/${requireValidUuid(grantId, "grantId")}/revoke`,
        undefined,
        { query: { tenantId: requireValidUuid(tenantId, "tenantId") } },
      );
    },
  };
}

export const tenantAccessApi = createTenantAccessApi();
