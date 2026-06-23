/**
 * Auth API — typed client for the SANAD authentication backend.
 *
 * Backend contract (from AuthController.java):
 *   POST  /api/v1/auth/login                    → 200, AuthResponse (accessToken in body, refreshToken in Set-Cookie)
 *   POST  /api/v1/auth/refresh                  → 200, AuthResponse (rotated token pair)
 *   POST  /api/v1/auth/logout                   → 204 (revokes refresh tokens)
 *   GET   /api/v1/auth/me                       → 200, MeResponse (user + memberships + roleGrants)
 *   POST  /api/v1/auth/forgot-password          → 200, { message } (always succeeds — no enumeration)
 *   POST  /api/v1/auth/reset-password           → 200, { message } (resets password with token)
 *   POST  /api/v1/auth/change-credential        → 204 (self-service password change)
 *   POST  /api/v1/auth/admin-reset-password/:id → 200, { message } (admin password reset)
 *
 * The refresh token is delivered via HttpOnly cookie (BFF pattern).
 * In local/dev, a body fallback is available for testing.
 *
 * Login is email+password only — no tenantId required.
 */

import { apiClient, ApiClient } from "./client";

// ---------------------------------------------------------------------------
// Types (match backend DTOs)
// ---------------------------------------------------------------------------

export interface AuthUser {
  id: string;
  tenantId: string;
  email: string;
  displayName: string | null;
  status: string;
}

export interface AuthResponse {
  accessToken: string;
  expiresAt: string;
  user: AuthUser;
}

export interface LoginRequest {
  email: string;
  password: string;
  tenantId?: string;
}

export interface MeResponse {
  id: string;
  tenantId: string;
  email: string;
  displayName: string | null;
  status: string;
  lastLoginAt: string | null;
  credentialRotationRequired: boolean;
  memberships: MeMembership[];
  roleGrants: MeRoleGrant[];
}

export interface MeMembership {
  id: string;
  organizationId: string;
  status: string;
}

export interface MeRoleGrant {
  id: string;
  roleId: string;
  roleCode: string;
  organizationId: string | null;
  status: string;
}

/** Request body for forgot-password. */
export interface ForgotPasswordRequest {
  email: string;
}

/** Response from forgot-password (always success, no enumeration). */
export interface ForgotPasswordResponse {
  message: string;
  /** Only present in dev/local mode for testing. */
  token?: string;
  resetUrl?: string;
}

/** Request body for reset-password. */
export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

/** Response from reset-password. */
export interface ResetPasswordResponse {
  message: string;
}

/** Request body for change-credential (self-service). */
export interface ChangeCredentialRequest {
  currentCredential: string;
  newCredential: string;
}

/** Request body for admin-reset-password. */
export interface AdminResetPasswordRequest {
  newPassword: string;
  forceChange: boolean;
}

/** Error thrown when login finds the same email in multiple tenants (HTTP 409). */
export class AmbiguousTenantError extends Error {
  readonly tenantIds: string[];
  constructor(message: string, tenantIds: string[]) {
    super(message);
    this.name = "AmbiguousTenantError";
    this.tenantIds = tenantIds;
  }
}

// ---------------------------------------------------------------------------
// API
// ---------------------------------------------------------------------------

export function createAuthApi(client: ApiClient = apiClient) {
  return {
    async login(req: LoginRequest): Promise<AuthResponse> {
      try {
        return await client.post<AuthResponse, LoginRequest>("/api/v1/auth/login", req);
      } catch (err) {
        // Handle 409 Ambiguous Tenant — extract tenant IDs from the response
        if (
          err &&
          typeof err === "object" &&
          "status" in err &&
          (err as { status: number }).status === 409 &&
          "body" in err
        ) {
          const body = (err as { body?: Record<string, unknown> }).body;
          const tenantIds = Array.isArray(body?.tenantIds)
            ? (body.tenantIds as string[])
            : [];
          const message =
            typeof body?.message === "string"
              ? body.message
              : "البريد الإلكتروني موجود في عدة مستأجرين";
          throw new AmbiguousTenantError(message, tenantIds);
        }
        throw err;
      }
    },

    async refresh(refreshToken?: string): Promise<AuthResponse> {
      const body = refreshToken ? { refreshToken } : undefined;
      return client.post<AuthResponse, { refreshToken?: string }>("/api/v1/auth/refresh", body);
    },

    async logout(): Promise<void> {
      await client.post<void>("/api/v1/auth/logout");
    },

    async me(): Promise<MeResponse> {
      return client.get<MeResponse>("/api/v1/auth/me");
    },

    async forgotPassword(req: ForgotPasswordRequest): Promise<ForgotPasswordResponse> {
      return client.post<ForgotPasswordResponse, ForgotPasswordRequest>(
        "/api/v1/auth/forgot-password",
        req,
      );
    },

    async resetPassword(req: ResetPasswordRequest): Promise<ResetPasswordResponse> {
      return client.post<ResetPasswordResponse, ResetPasswordRequest>(
        "/api/v1/auth/reset-password",
        req,
      );
    },

    async changeCredential(req: ChangeCredentialRequest): Promise<void> {
      await client.post<void, ChangeCredentialRequest>(
        "/api/v1/auth/change-credential",
        req,
      );
    },

    async adminResetPassword(
      userId: string,
      req: AdminResetPasswordRequest,
    ): Promise<{ message: string }> {
      return client.post<{ message: string }, AdminResetPasswordRequest>(
        `/api/v1/auth/admin-reset-password/${userId}`,
        req,
      );
    },
  };
}

export const authApi = createAuthApi();
