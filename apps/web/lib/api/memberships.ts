/**
 * Organization Memberships API — typed client for the SANAD backend.
 *
 * Backend contract (from OrganizationMembershipController.java):
 *   POST   /api/v1/organizations/{organizationId}/memberships?tenantId=...
 *          body: { tenantId, organizationId, email, displayName }
 *          → 201, OrganizationMembershipResponse
 *   GET    /api/v1/organizations/{organizationId}/memberships?tenantId=...
 *          → 200, List<OrganizationMembershipResponse>
 *   GET    /api/v1/organizations/{organizationId}/memberships/{membershipId}?tenantId=...
 *          → 200, OrganizationMembershipResponse
 *   PATCH  /api/v1/organizations/{organizationId}/memberships/{membershipId}/{action}?tenantId=...
 *          where action ∈ {activate, deactivate, remove}
 *          → 200, OrganizationMembershipResponse
 *
 * Tenant identity is ALWAYS a query parameter (never a header).
 * Organization identity is in the URL path.
 *
 * CRITICAL: For invite (POST), the body MUST contain tenantId and
 * organizationId that EXACTLY MATCH the query param and path var.
 * The backend service reads these from the body, not from the URL.
 * This client enforces consistency and rejects mismatched values.
 *
 * `remove` is a SOFT DELETE (status → REMOVED), not an HTTP DELETE.
 */

import { apiClient, ApiClient } from "./client";
import {
  requireValidUuid,
  requireValidEmail,
  requireValidDisplayName,
} from "./validation";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/**
 * Membership status values (matches Backend MembershipStatus enum).
 * Note: NO `SUSPENDED` or `ARCHIVED` (unlike UserStatus).
 * Note: HAS `REMOVED` (unlike UserStatus).
 */
export type MembershipStatus = "ACTIVE" | "INACTIVE" | "INVITED" | "REMOVED";

/**
 * Organization membership response shape (matches Backend OrganizationMembershipResponse).
 * `userId` is null until a User is linked to the membership.
 */
export interface OrganizationMembershipResponse {
  id: string;
  tenantId: string;
  organizationId: string;
  userId: string | null;
  email: string;
  displayName: string | null;
  status: MembershipStatus;
  createdAt: string;
  updatedAt: string;
}

/**
 * Request body for inviting a member.
 * tenantId and organizationId MUST match the URL query/path values.
 */
export interface InviteOrganizationMemberRequest {
  tenantId: string;
  organizationId: string;
  email: string;
  displayName: string | null;
}

/**
 * Lifecycle actions for membership status transitions.
 * Note: NO `suspend` or `archive` (unlike users).
 * Note: `remove` is a soft delete (status → REMOVED).
 */
export type MembershipLifecycleAction = "activate" | "deactivate" | "remove";

// ---------------------------------------------------------------------------
// Validation helpers
// ---------------------------------------------------------------------------

function requireValidLifecycleAction(action: string): MembershipLifecycleAction {
  const allowed: MembershipLifecycleAction[] = ["activate", "deactivate", "remove"];
  if (!allowed.includes(action as MembershipLifecycleAction)) {
    throw new Error(`إجراء دورة حياة غير صالح: ${action}`);
  }
  return action as MembershipLifecycleAction;
}

// ---------------------------------------------------------------------------
// API factory + singleton
// ---------------------------------------------------------------------------

/**
 * Create a Memberships API client bound to a specific ApiClient instance.
 * Defaults to the singleton `apiClient`.
 */
export function createMembershipsApi(client: ApiClient = apiClient) {
  return {
    /**
     * List all memberships in an organization.
     * GET /api/v1/organizations/{organizationId}/memberships?tenantId=...
     */
    async list(tenantId: string, organizationId: string) {
      return client.get<OrganizationMembershipResponse[]>(
        `/api/v1/organizations/${requireValidUuid(organizationId, "organizationId")}/memberships`,
        { query: { tenantId: requireValidUuid(tenantId, "tenantId") } }
      );
    },

    /**
     * Get a single membership by ID.
     * GET /api/v1/organizations/{organizationId}/memberships/{membershipId}?tenantId=...
     */
    async get(tenantId: string, organizationId: string, membershipId: string) {
      return client.get<OrganizationMembershipResponse>(
        `/api/v1/organizations/${requireValidUuid(organizationId, "organizationId")}/memberships/${requireValidUuid(membershipId, "membershipId")}`,
        { query: { tenantId: requireValidUuid(tenantId, "tenantId") } }
      );
    },

    /**
     * Invite a new member to an organization.
     * POST /api/v1/organizations/{organizationId}/memberships?tenantId=...
     *
     * The body MUST contain tenantId and organizationId that EXACTLY MATCH
     * the query param and path var. The backend service reads these from
     * the body, not from the URL. This client enforces consistency.
     *
     * Email is trimmed + lowercased before sending.
     * DisplayName is trimmed, empty → null.
     */
    async invite(
      tenantId: string,
      organizationId: string,
      input: { email: string; displayName?: string | null }
    ) {
      const validTenantId = requireValidUuid(tenantId, "tenantId");
      const validOrgId = requireValidUuid(organizationId, "organizationId");
      const body: InviteOrganizationMemberRequest = {
        tenantId: validTenantId,
        organizationId: validOrgId,
        email: requireValidEmail(input.email),
        displayName: requireValidDisplayName(input.displayName ?? null),
      };
      return client.post<OrganizationMembershipResponse, InviteOrganizationMemberRequest>(
        `/api/v1/organizations/${validOrgId}/memberships`,
        body,
        { query: { tenantId: validTenantId } }
      );
    },

    /**
     * Transition a membership's lifecycle status.
     * PATCH /api/v1/organizations/{organizationId}/memberships/{membershipId}/{action}?tenantId=...
     *
     * @param action — one of: activate, deactivate, remove
     *
     * `remove` is a SOFT DELETE (status → REMOVED), NOT an HTTP DELETE.
     * The row is retained for audit.
     */
    async transition(
      tenantId: string,
      organizationId: string,
      membershipId: string,
      action: MembershipLifecycleAction
    ) {
      const validAction = requireValidLifecycleAction(action);
      return client.patch<OrganizationMembershipResponse>(
        `/api/v1/organizations/${requireValidUuid(organizationId, "organizationId")}/memberships/${requireValidUuid(membershipId, "membershipId")}/${validAction}`,
        undefined,
        { query: { tenantId: requireValidUuid(tenantId, "tenantId") } }
      );
    },
  };
}

/**
 * Singleton Memberships API client.
 */
export const membershipsApi = createMembershipsApi();
