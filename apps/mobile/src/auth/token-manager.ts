/**
 * G7 Auth Module — JWT Token Management
 *
 * Requirements: SEC-001, SEC-015, SEC-016, ARCH-002
 * ADR Reference: C2 Decision (7-day refresh token)
 *
 * Token Lifecycle:
 *   Access Token:  15 minutes, RS256, memory only
 *   Refresh Token: 7 days, opaque, secure storage
 *   Rotation:      On each refresh, new refresh token issued
 *   Revocation:    Server-side revocation list
 */

import * as SecureStore from 'expo-secure-store';
import { AuthTokens } from '../types';

// ═══════════════════════════════════════════════════════════
// CONSTANTS (per C2 Decision: 7-day refresh token)
// ═══════════════════════════════════════════════════════════

const ACCESS_TOKEN_TTL_MS = 15 * 60 * 1000;        // 15 minutes
const REFRESH_TOKEN_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7 days
const TOKEN_REFRESH_BUFFER_MS = 60 * 1000;          // Refresh 1 min before expiry

const SECURE_STORE_KEYS = {
  ACCESS_TOKEN: 'g7_access_token',
  REFRESH_TOKEN: 'g7_refresh_token',
  TOKEN_EXPIRY: 'g7_token_expiry',
  REFRESH_EXPIRY: 'g7_refresh_expiry',
} as const;

// ═══════════════════════════════════════════════════════════
// TOKEN MANAGER
// ═══════════════════════════════════════════════════════════

export class TokenManager {
  private accessToken: string | null = null;
  private tokenExpiry: number = 0;

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
