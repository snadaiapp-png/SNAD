/**
 * Auth API — typed client for the SANAD authentication backend.
 *
 * Backend contract (from AuthController.java):
 *   POST  /api/v1/auth/login    → 200, AuthResponse (accessToken in body, refreshToken in Set-Cookie)
 *   POST  /api/v1/auth/refresh  → 200, AuthResponse (rotated token pair)
 *   POST  /api/v1/auth/logout   → 204 (revokes refresh tokens)
 *   GET   /api/v1/auth/me       → 200, MeResponse (user + memberships + roleGrants)
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

// ---------------------------------------------------------------------------
// API
// ---------------------------------------------------------------------------

export function createAuthApi(client: ApiClient = apiClient) {
  return {
    async login(req: LoginRequest): Promise<AuthResponse> {
      return client.post<AuthResponse, LoginRequest>("/api/v1/auth/login", req);
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
  };
}

export const authApi = createAuthApi();
