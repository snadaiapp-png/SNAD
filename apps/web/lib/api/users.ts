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
  displayName: string | null;
  status: UserStatus;
  createdAt: string;
  updatedAt: string;
}

/**
 * Request body for creating a user.
 * `status` is optional — backend defaults to INVITED when omitted.
 */
export interface CreateUserRequest {
  email: string;
  displayName: string | null;
  status?: UserStatus;
}

/**
 * Request body for updating a user.
 * No `status` field — lifecycle changes use PATCH endpoints.
 */
export interface UpdateUserRequest {
  email: string;
  displayName: string | null;
}

/**
 * Lifecycle actions for user status transitions.
 */
export type UserLifecycleAction = "activate" | "deactivate" | "suspend" | "archive";

// ---------------------------------------------------------------------------
// Validation helpers
// ---------------------------------------------------------------------------

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
     * Status defaults to INVITED if omitted.
     */
    async create(
      tenantId: string,
      input: { email: string; displayName?: string | null; status?: UserStatus }
    ) {
      const body: CreateUserRequest = {
        email: requireValidEmail(input.email),
        displayName: requireValidDisplayName(input.displayName ?? null),
      };
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
      input: { email: string; displayName?: string | null }
    ) {
      const body: UpdateUserRequest = {
        email: requireValidEmail(input.email),
        displayName: requireValidDisplayName(input.displayName ?? null),
      };
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
