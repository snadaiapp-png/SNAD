/**
 * G7 Auth Module — JWT Token Management  (extended for R2-I)
 *
 * Requirements: SEC-001, SEC-015, SEC-016, ARCH-002
 * ADR Reference: C2 Decision (7-day refresh token)
 * R2-I: real /api/v1/auth/login + /api/v1/auth/refresh + /api/v1/auth/logout
 *       integration with single-flight refresh semantics.
 *
 * Token Lifecycle:
 *   Access Token:  short-lived (expiresAt from server), memory only
 *   Refresh Token: 7 days, opaque, secure storage
 *   Rotation:      On each refresh, new refresh token issued (via the
 *                  X-SANAD-Refresh-Token response header — the backend
 *                  NEVER serializes the refresh token into the JSON body)
 *   Revocation:    Server-side revocation list (POST /auth/logout)
 *
 * IMPORTANT: mobile UI state is NEVER authorization. This module only
 * transports credentials; every capability decision is revalidated
 * server-side (@RequireCapability + WorkflowActionabilityService).
 */

import * as SecureStore from 'expo-secure-store';
import { AuthTokens } from '../types';
import { getApiBaseUrl } from '../config/api';

// ═══════════════════════════════════════════════════════════
// CONSTANTS (per C2 Decision: 7-day refresh token)
// ═══════════════════════════════════════════════════════════

const ACCESS_TOKEN_TTL_MS = 15 * 60 * 1000;        // 15 minutes (fallback)
const REFRESH_TOKEN_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7 days
const TOKEN_REFRESH_BUFFER_MS = 60 * 1000;          // Refresh 1 min before expiry

/**
 * The backend transfers the refresh token ONLY through this response
 * header (AuthController.REFRESH_TOKEN_HEADER); the JSON body never
 * contains it (@JsonIgnore on AuthResponse.refreshToken).
 */
export const REFRESH_TOKEN_HEADER = 'X-SANAD-Refresh-Token';

const SECURE_STORE_KEYS = {
  ACCESS_TOKEN: 'g7_access_token',
  REFRESH_TOKEN: 'g7_refresh_token',
  TOKEN_EXPIRY: 'g7_token_expiry',
  REFRESH_EXPIRY: 'g7_refresh_expiry',
} as const;

// ═══════════════════════════════════════════════════════════
// AUTH ERROR (local to auth module; interceptor re-exports its own)
// ═══════════════════════════════════════════════════════════

export class TokenManagerError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.code = code;
    this.name = 'TokenManagerError';
  }
}

// ═══════════════════════════════════════════════════════════
// TOKEN MANAGER
// ═══════════════════════════════════════════════════════════

export class TokenManager {
  private accessToken: string | null = null;
  private tokenExpiry: number = 0;
  private refreshInFlight: Promise<boolean> | null = null;

  /**
   * Store tokens in secure storage after successful authentication.
   */
  async storeTokens(tokens: AuthTokens): Promise<void> {
    const now = Date.now();

    // Access token: memory only (not persisted per security spec)
    this.accessToken = tokens.accessToken;
    this.tokenExpiry = now + ACCESS_TOKEN_TTL_MS;

    // Refresh token: secure storage (Keychain/Keystore)
    await SecureStore.setItemAsync(
      SECURE_STORE_KEYS.REFRESH_TOKEN,
      tokens.refreshToken
    );

    await SecureStore.setItemAsync(
      SECURE_STORE_KEYS.REFRESH_EXPIRY,
      String(now + REFRESH_TOKEN_TTL_MS)
    );
  }

  /**
   * Get current access token. Returns null if expired or not available.
   */
  getAccessToken(): string | null {
    if (!this.accessToken) return null;
    if (Date.now() >= this.tokenExpiry) {
      this.accessToken = null;
      return null;
    }
    return this.accessToken;
  }

  /**
   * Check if access token needs refresh (within buffer period of expiry).
   */
  needsRefresh(): boolean {
    if (!this.accessToken) return true;
    return Date.now() >= this.tokenExpiry - TOKEN_REFRESH_BUFFER_MS;
  }

  /**
   * Check if refresh token is still valid.
   */
  async hasValidRefreshToken(): Promise<boolean> {
    try {
      const expiryStr = await SecureStore.getItemAsync(SECURE_STORE_KEYS.REFRESH_EXPIRY);
      if (!expiryStr) return false;

      const expiry = parseInt(expiryStr, 10);
      return Date.now() < expiry;
    } catch {
      return false;
    }
  }

  /**
   * Get refresh token from secure storage.
   */
  async getRefreshToken(): Promise<string | null> {
    try {
      return await SecureStore.getItemAsync(SECURE_STORE_KEYS.REFRESH_TOKEN);
    } catch {
      return null;
    }
  }

  /**
   * Update access token after successful refresh.
   * New refresh token is also stored (rotation).
   */
  async refreshAccessToken(newTokens: AuthTokens): Promise<void> {
    this.accessToken = newTokens.accessToken;
    this.tokenExpiry = Date.now() + ACCESS_TOKEN_TTL_MS;

    // Rotate refresh token
    await SecureStore.setItemAsync(
      SECURE_STORE_KEYS.REFRESH_TOKEN,
      newTokens.refreshToken
    );

    await SecureStore.setItemAsync(
      SECURE_STORE_KEYS.REFRESH_EXPIRY,
      String(Date.now() + REFRESH_TOKEN_TTL_MS)
    );
  }

  /**
   * Clear all tokens (on logout or revocation).
   */
  async clearTokens(): Promise<void> {
    this.accessToken = null;
    this.tokenExpiry = 0;

    await SecureStore.deleteItemAsync(SECURE_STORE_KEYS.REFRESH_TOKEN);
    await SecureStore.deleteItemAsync(SECURE_STORE_KEYS.REFRESH_EXPIRY);
  }

  // ═════════════════════════════════════════════════════════
  // R2-I: REAL AUTH FLOWS (login / refresh / logout)
  // ═════════════════════════════════════════════════════════

  /**
   * Sign in against POST <base>/api/v1/auth/login with {email, password}
   * (backend LoginRequest field names, verified against AuthController).
   *
   * The response body carries {accessToken, expiresAt, ...}; the refresh
   * token arrives ONLY in the X-SANAD-Refresh-Token response header.
   */
  async login(email: string, password: string): Promise<void> {
    let response: Response;
    try {
      response = await fetch(`${getApiBaseUrl()}/api/v1/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });
    } catch {
      throw new TokenManagerError('NETWORK', 'Sign-in failed — network error.');
    }

    if (!response.ok) {
      if (response.status === 401 || response.status === 403) {
        throw new TokenManagerError('INVALID_CREDENTIALS', 'Invalid email or password.');
      }
      throw new TokenManagerError(
        'LOGIN_FAILED',
        `Sign-in failed (HTTP ${response.status}).`
      );
    }

    const body = await response.json().catch(() => null);
    const accessToken: unknown = body?.accessToken;
    const refreshToken: unknown = response.headers.get(REFRESH_TOKEN_HEADER);

    if (typeof accessToken !== 'string' || accessToken.length === 0) {
      throw new TokenManagerError('MALFORMED_RESPONSE', 'Sign-in response missing access token.');
    }
    if (typeof refreshToken !== 'string' || refreshToken.length === 0) {
      throw new TokenManagerError('MALFORMED_RESPONSE', 'Sign-in response missing refresh token.');
    }

    const expiresAt: unknown = body?.expiresAt;
    const expiresIn =
      typeof expiresAt === 'string' && Number.isFinite(Date.parse(expiresAt))
        ? Math.max(0, Math.floor((Date.parse(expiresAt) - Date.now()) / 1000))
        : 0;

    await this.storeTokens({ accessToken, refreshToken, expiresIn, tokenType: 'Bearer' });
  }

  /**
   * Single-flight session refresh against POST <base>/api/v1/auth/refresh.
   *
   * Concurrent callers share one HTTP round-trip. The current refresh token
   * is presented via the X-SANAD-Refresh-Token header (production path) AND
   * the JSON body {refreshToken} (honored on local/dev profiles) — verified
   * against AuthController#refresh. On success the rotated refresh token
   * from the response header is persisted (rotation), returning true.
   * Any failure returns false (never throws).
   */
  refreshSession(): Promise<boolean> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.doRefresh().finally(() => {
        this.refreshInFlight = null;
      });
    }
    return this.refreshInFlight;
  }

  private async doRefresh(): Promise<boolean> {
    try {
      const refreshToken = await this.getRefreshToken();
      if (!refreshToken) return false;

      const response = await fetch(`${getApiBaseUrl()}/api/v1/auth/refresh`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          [REFRESH_TOKEN_HEADER]: refreshToken,
        },
        body: JSON.stringify({ refreshToken }),
      });

      if (!response.ok) return false;

      const body = await response.json().catch(() => null);
      const accessToken: unknown = body?.accessToken;
      if (typeof accessToken !== 'string' || accessToken.length === 0) return false;

      const rotated: unknown = response.headers.get(REFRESH_TOKEN_HEADER);
      await this.refreshAccessToken({
        accessToken,
        refreshToken:
          typeof rotated === 'string' && rotated.length > 0 ? rotated : refreshToken,
        expiresIn: 0,
        tokenType: 'Bearer',
      });
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Sign out: best-effort POST <base>/api/v1/auth/logout (revokes ALL
   * refresh tokens server-side; 204), then clear local tokens. Local
   * clearing happens even if the server call fails.
   */
  async logout(): Promise<void> {
    const accessToken = this.getAccessToken();
    if (accessToken) {
      try {
        await fetch(`${getApiBaseUrl()}/api/v1/auth/logout`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${accessToken}` },
        });
      } catch {
        // Best-effort: the local session is invalidated regardless.
      }
    }
    await this.clearTokens();
  }

  // ═════════════════════════════════════════════════════════
  // SYNC STATE (unchanged G7 surface)
  // ═════════════════════════════════════════════════════════

  /**
   * Get time until access token expires (ms). Returns 0 if expired.
   */
  getTimeUntilExpiry(): number {
    const remaining = this.tokenExpiry - Date.now();
    return Math.max(0, remaining);
  }

  /**
   * Get sync state based on token status.
   */
  async getSyncState(): Promise<'ONLINE' | 'REAUTH_REQUIRED'> {
    if (this.getAccessToken()) return 'ONLINE';
    if (await this.hasValidRefreshToken()) return 'ONLINE';
    return 'REAUTH_REQUIRED';
  }
}

// ═══════════════════════════════════════════════════════════
// SINGLETON
// ═══════════════════════════════════════════════════════════

let instance: TokenManager | null = null;

export function getTokenManager(): TokenManager {
  if (!instance) {
    instance = new TokenManager();
  }
  return instance;
}
