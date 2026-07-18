import { apiClient, ApiClient } from "./client";
import { ApiHttpError } from "./errors";

/**
 * Keep the browser-side budget greater than the BFF's maximum upstream budget
 * (25 seconds) so the frontend receives the BFF's definitive 502/503/504
 * response instead of aborting early and masking the real failure as a generic
 * client timeout.
 */
const AUTH_REQUEST_TIMEOUT_MS = 30_000;

export interface AuthUser {
  id: string;
  tenantId: string;
  email: string;
  displayName: string | null;
  status: string;
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

export interface TenantContext {
  tenantId: string;
  defaultOrganizationId: string | null;
}

/** Complete post-authentication bootstrap returned by login and refresh. */
export interface AuthResponse {
  accessToken: string;
  expiresAt: string;
  user: AuthUser;
  lastLoginAt: string | null;
  credentialRotationRequired: boolean;
  memberships: MeMembership[];
  effectiveRoleGrants: MeRoleGrant[];
  defaultOrganizationId: string | null;
  defaultDestination: string;
  availableDestinations: string[];
  tenantContext: TenantContext;
}

export interface LoginRequest { email: string; password: string; tenantId?: string; }
export interface MeResponse extends AuthUser {
  lastLoginAt: string | null;
  credentialRotationRequired: boolean;
  memberships: MeMembership[];
  roleGrants: MeRoleGrant[];
}
export interface ForgotPasswordRequest { email: string; }
export interface ForgotPasswordResponse { message: string; token?: string; resetUrl?: string; }
export interface ResetPasswordRequest { token: string; newPassword: string; }
export interface ResetPasswordResponse { message: string; }
export interface ChangeCredentialRequest { currentCredential: string; newCredential: string; }
export interface AdminResetPasswordRequest { locale?: "ar" | "en"; }

export class AmbiguousTenantError extends Error {
  readonly tenantIds: string[];
  constructor(message: string, tenantIds: string[]) {
    super(message);
    this.name = "AmbiguousTenantError";
    this.tenantIds = tenantIds;
  }
}

export function authResponseToMe(response: AuthResponse): MeResponse {
  return {
    ...response.user,
    lastLoginAt: response.lastLoginAt,
    credentialRotationRequired: response.credentialRotationRequired,
    memberships: response.memberships,
    roleGrants: response.effectiveRoleGrants,
  };
}

export function createAuthApi(client: ApiClient = apiClient) {
  return {
    async login(req: LoginRequest): Promise<AuthResponse> {
      try {
        return await client.post<AuthResponse, LoginRequest>("/api/v1/auth/login", req, {
          timeoutMs: AUTH_REQUEST_TIMEOUT_MS,
        });
      } catch (err) {
        if (err instanceof ApiHttpError && err.status === 409) {
          const body = err.details.body;
          const tenantIds = Array.isArray(body?.tenantIds) ? body.tenantIds as string[] : [];
          throw new AmbiguousTenantError(
            typeof body?.message === "string" ? body.message : "البريد الإلكتروني مرتبط بأكثر من مستأجر",
            tenantIds,
          );
        }
        throw err;
      }
    },
    async refresh(refreshToken?: string): Promise<AuthResponse> {
      return client.post<AuthResponse, { refreshToken?: string }>(
        "/api/v1/auth/refresh",
        refreshToken ? { refreshToken } : undefined,
        { timeoutMs: AUTH_REQUEST_TIMEOUT_MS },
      );
    },
    async logout(): Promise<void> {
      await client.post<void>("/api/v1/auth/logout", undefined, { timeoutMs: AUTH_REQUEST_TIMEOUT_MS });
    },
    async me(): Promise<MeResponse> {
      return client.get<MeResponse>("/api/v1/auth/me", { timeoutMs: AUTH_REQUEST_TIMEOUT_MS });
    },
    async forgotPassword(req: ForgotPasswordRequest): Promise<ForgotPasswordResponse> {
      return client.post<ForgotPasswordResponse, ForgotPasswordRequest>("/api/v1/auth/forgot-password", req, {
        timeoutMs: AUTH_REQUEST_TIMEOUT_MS,
      });
    },
    async resetPassword(req: ResetPasswordRequest): Promise<ResetPasswordResponse> {
      return client.post<ResetPasswordResponse, ResetPasswordRequest>("/api/v1/auth/reset-password", req, {
        timeoutMs: AUTH_REQUEST_TIMEOUT_MS,
      });
    },
    async changeCredential(req: ChangeCredentialRequest): Promise<void> {
      await client.post<void, ChangeCredentialRequest>("/api/v1/auth/change-credential", req, {
        timeoutMs: AUTH_REQUEST_TIMEOUT_MS,
      });
    },
    async adminResetPassword(
      userId: string,
      req: AdminResetPasswordRequest = {},
    ): Promise<{ message: string }> {
      return client.post<{ message: string }, AdminResetPasswordRequest>(
        `/api/v1/auth/admin-reset-password/${userId}`,
        req,
        { timeoutMs: AUTH_REQUEST_TIMEOUT_MS },
      );
    },
  };
}

export const authApi = createAuthApi();
