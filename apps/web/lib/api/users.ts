/**
 * Users API — typed client for the SANAD Users backend.
 *
 * Backend contract (from UserController.java):
 *   POST   /api/v1/users?tenantId=...                       → 201, UserResponse
 *   GET    /api/v1/users?tenantId=...                       → 200, List<UserResponse>
 *   GET    /api/v1/users/{userId}?tenantId=...              → 200, UserResponse
 *   PUT    /api/v1/users/{userId}?tenantId=...              → 200, UserResponse
 *   PATCH  /api/v1/users/{userId}/{action}?tenantId=...     → 200, UserResponse
 *     where action ∈ {activate, deactivate, suspend, archive}
 *
 * Tenant identity is ALWAYS a query parameter (never a header).
 * No authentication in this phase.
 *
 * All inputs are validated client-side before transport:
 * - tenantId, userId must be valid UUIDs
 * - email is trimmed + lowercased, max 255 chars
 * - displayName is trimmed, empty → null, max 200 chars
 */

import { apiClient, ApiClient } from "./client";
import { ApiConfigurationError } from "./errors";
import {
  requireValidUuid,
  requireValidEmail,
  requireValidDisplayName,
} from "./validation";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/**
 * User status values (matches Backend UserStatus enum).
 */
export type UserStatus = "ACTIVE" | "INACTIVE" | "INVITED" | "SUSPENDED" | "ARCHIVED";

/**
 * User response shape (matches Backend UserResponse).
 */
export interface UserResponse {
  id: string;
  tenantId: string;
  email: string;
  username: string | null;
  displayName: string | null;
  mobileNumber: string | null;
  mobileRegion: string | null;
  status: UserStatus;
  lastLoginAt: string | null;
  credentialInitialized: boolean;
  credentialRotationRequired: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * Request body for creating a user.
 * `status` is optional. When `initialCredential` is supplied, the backend creates
 * an ACTIVE account and requires credential rotation at first sign-in.
 */
export interface CreateUserRequest {
  email: string;
  username?: string | null;
  displayName: string | null;
  mobileNumber?: string | null;
  mobileRegion?: string | null;
  initialCredential?: string;
  status?: UserStatus;
}

/**
 * Request body for updating a user.
 * No `status` field — lifecycle changes use PATCH endpoints.
 */
export interface UpdateUserRequest {
  email: string;
  username?: string | null;
  displayName: string | null;
  mobileNumber?: string | null;
  mobileRegion?: string | null;
}

/**
 * Lifecycle actions for user status transitions.
 */
export type UserLifecycleAction = "activate" | "deactivate" | "suspend" | "archive";

export interface ModuleProvisioningRole {
  roleId: string;
  roleCode: string;
  roleName: string;
  capabilities: string[];
}

export interface ModuleProvisioningContext {
  applicationCode: string;
  name: string;
  localizedName: string | null;
  capabilityNamespaces: string[];
  declaredCapabilities: string[];
  supportedScopes: string[];
  roles: ModuleProvisioningRole[];
}

export interface ModuleUserAccessProjection {
  userId: string;
  email: string;
  username: string | null;
  displayName: string | null;
  status: UserStatus;
  effectiveAccess: boolean;
  assignedRoles: string[];
  effectiveCapabilities: string[];
}

// ---------------------------------------------------------------------------
// Validation helpers
// ---------------------------------------------------------------------------

function normalizeMobileNumber(value?: string | null): string | null {
  const normalized = value?.trim() ?? "";
  if (!normalized) return null;
  if (!/^\+[1-9][0-9]{7,14}$/.test(normalized)) {
    throw new ApiConfigurationError("رقم الجوال يجب أن يكون بصيغة E.164");
  }
  return normalized;
}

function normalizeMobileRegion(value?: string | null): string | null {
  const normalized = value?.trim().toUpperCase() ?? "";
  if (!normalized) return null;
  if (!/^[A-Z]{2}$/.test(normalized)) {
    throw new ApiConfigurationError("رمز المنطقة يجب أن يتكون من حرفين");
  }
  return normalized;
}

function requireValidInitialCredential(value?: string | null): string | undefined {
  if (value == null) return undefined;
  if (!value.trim()) return undefined;
  if (value.length < 8 || value.length > 256) {
    throw new ApiConfigurationError("كلمة المرور المؤقتة يجب أن تكون بين 8 و256 حرفًا");
  }
  return value;
}

function requireValidLifecycleAction(action: string): UserLifecycleAction {
  const allowed: UserLifecycleAction[] = ["activate", "deactivate", "suspend", "archive"];
  if (!allowed.includes(action as UserLifecycleAction)) {
    throw new Error(`إجراء دورة حياة غير صالح: ${action}`);
  }
  return action as UserLifecycleAction;
}

// ---------------------------------------------------------------------------
// API factory + singleton
// ---------------------------------------------------------------------------

/**
 * Create a Users API client bound to a specific ApiClient instance.
 * Defaults to the singleton `apiClient`.
 */
export function createUsersApi(client: ApiClient = apiClient) {
  return {
    /**
     * List all users in a tenant.
     * GET /api/v1/users?tenantId=...
     */
    async list(tenantId: string) {
      return client.get<UserResponse[]>("/api/v1/users", {
        query: { tenantId: requireValidUuid(tenantId, "tenantId") },
      });
    },

    async moduleProvisioningContext(tenantId: string, routeRoot: string) {
      const normalizedRouteRoot = routeRoot.trim().toLowerCase().replace(/^\/+/, "").split("/")[0];
      if (!normalizedRouteRoot) {
        throw new ApiConfigurationError("module route root is required");
      }
      return client.get<ModuleProvisioningContext>("/api/v1/users/module-provisioning-context", {
        query: {
          tenantId: requireValidUuid(tenantId, "tenantId"),
          routeRoot: normalizedRouteRoot,
        },
      });
    },

    async grantModuleCapabilities(
      tenantId: string,
      targetUserId: string,
      routeRoot: string,
      capabilityCodes: string[],
    ) {
      const normalizedRouteRoot = routeRoot.trim().toLowerCase().replace(/^\/+/, "").split("/")[0];
      if (!normalizedRouteRoot) {
        throw new ApiConfigurationError("module route root is required");
      }
      if (capabilityCodes.length === 0) {
        throw new ApiConfigurationError("at least one module capability is required");
      }
      return client.post<void, {
        targetUserId: string;
        routeRoot: string;
        capabilityCodes: string[];
      }>(
        "/api/v1/users/module-capability-grants",
        {
          targetUserId: requireValidUuid(targetUserId, "targetUserId"),
          routeRoot: normalizedRouteRoot,
          capabilityCodes,
        },
        { query: { tenantId: requireValidUuid(tenantId, "tenantId") } },
      );
    },

    async grantModuleCapabilityOverrides(
      tenantId: string,
      targetUserId: string,
      routeRoot: string,
      capabilityCodes: string[],
    ) {
      const normalizedRouteRoot = routeRoot.trim().toLowerCase().replace(/^\\/+/, "").split("/")[0];
      if (!normalizedRouteRoot) {
        throw new ApiConfigurationError("module route root is required");
      }
      if (capabilityCodes.length === 0) {
        throw new ApiConfigurationError("at least one module capability is required");
      }
      return client.post<void, {
        targetUserId: string;
        routeRoot: string;
        capabilityCodes: string[];
      }>(
        "/api/v1/users/module-capability-overrides",
        {
          targetUserId: requireValidUuid(targetUserId, "targetUserId"),
          routeRoot: normalizedRouteRoot,
          capabilityCodes,
        },
        { query: { tenantId: requireValidUuid(tenantId, "tenantId") } },
      );
    },

    async moduleContext(routeRoot: string) {
      const normalizedRouteRoot = routeRoot.trim().toLowerCase().replace(/^\/+/, "").split("/")[0];
      if (!normalizedRouteRoot) {
        throw new ApiConfigurationError("module route root is required");
      }
      return client.get<ModuleProvisioningContext>("/api/v1/users/module-context", {
        query: { routeRoot: normalizedRouteRoot },
      });
    },

    async listModuleAccessUsers(routeRoot: string) {
      const normalizedRouteRoot = routeRoot.trim().toLowerCase().replace(/^\/+/, "").split("/")[0];
      if (!normalizedRouteRoot) {
        throw new ApiConfigurationError("module route root is required");
      }
      return client.get<ModuleUserAccessProjection[]>("/api/v1/users/module-access-users", {
        query: { routeRoot: normalizedRouteRoot },
      });
    },

    /**
     * Get a single user by ID.
     * GET /api/v1/users/{userId}?tenantId=...
     */
    async get(tenantId: string, userId: string) {
      return client.get<UserResponse>(
        `/api/v1/users/${requireValidUuid(userId, "userId")}`,
        { query: { tenantId: requireValidUuid(tenantId, "tenantId") } }
      );
    },

    /**
     * Create a new user.
     * POST /api/v1/users?tenantId=...
     *
     * Email is trimmed + lowercased before sending.
     * DisplayName is trimmed, empty → null.
     * Supplying initialCredential creates an immediately sign-in capable ACTIVE user
     * with mandatory first-login credential rotation.
     */
    async create(
      tenantId: string,
      input: { email: string; username?: string | null; displayName?: string | null; mobileNumber?: string | null; mobileRegion?: string | null; initialCredential?: string | null; status?: UserStatus }
    ) {
      const body: CreateUserRequest = {
        email: requireValidEmail(input.email),
        displayName: requireValidDisplayName(input.displayName ?? null),
      };
      if (input.username !== undefined) {
        body.username = input.username?.trim().toLowerCase() || null;
      }
      if (input.mobileNumber !== undefined) body.mobileNumber = normalizeMobileNumber(input.mobileNumber);
      if (input.mobileRegion !== undefined) body.mobileRegion = normalizeMobileRegion(input.mobileRegion);
      const initialCredential = requireValidInitialCredential(input.initialCredential);
      if (initialCredential !== undefined) body.initialCredential = initialCredential;
      if (input.status !== undefined) {
        body.status = input.status;
      }
      return client.post<UserResponse, CreateUserRequest>(
        "/api/v1/users",
        body,
        { query: { tenantId: requireValidUuid(tenantId, "tenantId") } }
      );
    },

    /**
     * Update a user's email and/or display name.
     * PUT /api/v1/users/{userId}?tenantId=...
     *
     * Does NOT send a status field — use transition() for lifecycle changes.
     */
    async update(
      tenantId: string,
      userId: string,
      input: { email: string; username?: string | null; displayName?: string | null; mobileNumber?: string | null; mobileRegion?: string | null }
    ) {
      const body: UpdateUserRequest = {
        email: requireValidEmail(input.email),
        displayName: requireValidDisplayName(input.displayName ?? null),
      };
      if (input.username !== undefined) {
        body.username = input.username?.trim().toLowerCase() || null;
      }
      if (input.mobileNumber !== undefined) body.mobileNumber = normalizeMobileNumber(input.mobileNumber);
      if (input.mobileRegion !== undefined) body.mobileRegion = normalizeMobileRegion(input.mobileRegion);
      return client.put<UserResponse, UpdateUserRequest>(
        `/api/v1/users/${requireValidUuid(userId, "userId")}`,
        body,
        { query: { tenantId: requireValidUuid(tenantId, "tenantId") } }
      );
    },

    /**
     * Transition a user's lifecycle status.
     * PATCH /api/v1/users/{userId}/{action}?tenantId=...
     *
     * @param action — one of: activate, deactivate, suspend, archive
     */
    async transition(tenantId: string, userId: string, action: UserLifecycleAction) {
      const validAction = requireValidLifecycleAction(action);
      return client.patch<UserResponse>(
        `/api/v1/users/${requireValidUuid(userId, "userId")}/${validAction}`,
        undefined,
        { query: { tenantId: requireValidUuid(tenantId, "tenantId") } }
      );
    },
  };
}

/**
 * Singleton Users API client.
 */
export const usersApi = createUsersApi();