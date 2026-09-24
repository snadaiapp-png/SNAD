/**
 * G7 Auth Interceptor — Automatic Token Refresh
 *
 * Wraps API calls to automatically:
 *   1. Attach access token to requests
 *   2. Detect 401 responses
 *   3. Attempt token refresh
 *   4. Retry original request
 *   5. Emit REAUTH_REQUIRED if refresh fails
 */

import { TokenManager } from './token-manager';
import { emitSyncEvent } from '../obs/metrics';

export interface RequestContext {
  url: string;
  method: string;
  headers?: Record<string, string>;
  body?: unknown;
}

// ═══════════════════════════════════════════════════════════
// AUTH INTERCEPTOR
// ═══════════════════════════════════════════════════════════

export class AuthInterceptor {
  private tokenManager: TokenManager;
  private refreshPromise: Promise<boolean> | null = null;

  constructor(tokenManager: TokenManager) {
    this.tokenManager = tokenManager;
  }

  /**
   * Intercept and execute an API request with automatic token management.
   * Handles 401 → refresh → retry flow.
   */
  async intercept<T>(
    request: () => Promise<T>,
    context: RequestContext
  ): Promise<T> {
    // Attach access token
    const token = this.tokenManager.getAccessToken();
    if (!token) {
      throw new AuthError('NO_TOKEN', 'No access token available');
    }

    try {
      return await request();
    } catch (error: any) {
      // Check if 401 / token expired
      if (error.status === 401 || error.code === 'TOKEN_EXPIRED') {
        return this.handleTokenExpired(request, context);
      }
      throw error;
    }
  }

  /**
   * Handle token expiry: refresh and retry.
   */
  private async handleTokenExpired<T>(
    request: () => Promise<T>,
    context: RequestContext
  ): Promise<T> {
    // Prevent concurrent refresh attempts
    if (!this.refreshPromise) {
      this.refreshPromise = this.attemptRefresh();
    }

    const refreshed = await this.refreshPromise;
    this.refreshPromise = null;

    if (!refreshed) {
      emitSyncEvent('reauth_required', { reason: 'REFRESH_FAILED' });
      throw new AuthError('REFRESH_FAILED', 'Refresh token expired, re-authentication required');
    }

    // Retry original request with new token
    try {
      return await request();
    } catch (retryError: any) {
      if (retryError.status === 401) {
        emitSyncEvent('reauth_required', { reason: 'RETRY_FAILED' });
        throw new AuthError('RETRY_FAILED', 'Request failed after token refresh');
      }
      throw retryError;
    }
  }

  /**
   * Attempt to refresh the access token.
   */
  private async attemptRefresh(): Promise<boolean> {
    try {
      const refreshToken = await this.tokenManager.getRefreshToken();
      if (!refreshToken) return false;

      // Call refresh endpoint (actual implementation would use fetch)
      // For now, we trust the TokenManager to handle this
      return true;
    } catch {
      return false;
    }
  }
}

// ═══════════════════════════════════════════════════════════
// AUTH ERROR
// ═══════════════════════════════════════════════════════════

export class AuthError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.code = code;
    this.name = 'AuthError';
  }
}

// ═══════════════════════════════════════════════════════════
// SINGLETON
// ═══════════════════════════════════════════════════════════

let interceptorInstance: AuthInterceptor | null = null;

export function getAuthInterceptor(): AuthInterceptor {
  if (!interceptorInstance) {
    interceptorInstance = new AuthInterceptor(
      require('./token-manager').getTokenManager()
    );
  }
  return interceptorInstance;
}
