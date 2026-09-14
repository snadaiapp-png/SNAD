/**
 * R2-I Mobile Action/Decision Center — typed workflow API client
 *
 * Wraps the REAL platform endpoints (verified against the backend):
 *   WorkflowController              @ /api/v1/workflows
 *     GET  /work-items/mine         GET  /work-items/pool
 *     POST /work-items/{id}/claim   body {expectedVersion, reason}
 *     POST /work-items/{id}/release body {expectedVersion, reason}
 *     POST /work-items/{id}/complete body {expectedVersion, reason}
 *     GET  /approvals/pending
 *     POST /approvals/{id}/approve  body {expectedVersion, comments}
 *     POST /approvals/{id}/reject   body {expectedVersion, comments}
 *   WorkflowNotificationController  @ /api/v1/workflows/notifications
 *     GET  /                        GET /unread-count
 *     POST /{id}/read               POST /read-all
 *
 * Error contract (surfaced to the UI, never cached):
 *   401 → single-flight refresh (TokenManager.refreshSession) + retry ONCE
 *   403 → AUTHORIZATION_REVOKED ("authorization revoked" — server-side
 *         @RequireCapability / WorkflowActionabilityService refused)
 *   409 → STALE ("stale — reload"; optimistic-lock version conflict)
 *
 * Mobile UI state is NEVER authorization: eligibility is decided by the
 * server on every mutation. The client only carries the optimistic-lock
 * `version` of the object it loaded and never caches actionability.
 */

import { TokenManager, getTokenManager } from '../auth/token-manager';
import { getApiBaseUrl } from '../config/api';
import { ApprovalDecision, WorkItem, WorkflowNotification } from './types';

const WORKFLOWS_PATH = '/api/v1/workflows';

export type WorkflowApiErrorCode =
  | 'AUTHORIZATION_REVOKED'
  | 'STALE'
  | 'UNAUTHENTICATED'
  | 'HTTP'
  | 'NETWORK';

export class WorkflowApiError extends Error {
  readonly code: WorkflowApiErrorCode;
  readonly status: number;

  constructor(code: WorkflowApiErrorCode, status: number, message: string) {
    super(message);
    this.code = code;
    this.status = status;
    this.name = 'WorkflowApiError';
  }
}

export interface WorkflowApiClientDeps {
  baseUrl?: string;
  tokenManager?: TokenManager;
  fetchImpl?: typeof fetch;
}

interface RawResponse {
  ok: boolean;
  status: number;
  headers: { get(name: string): string | null };
  json(): Promise<unknown>;
}

export class WorkflowApiClient {
  private readonly tokenManager: TokenManager;
  private readonly baseUrl: string;
  private readonly fetchImpl: (url: string, init?: RequestInit) => Promise<RawResponse>;

  constructor(deps: WorkflowApiClientDeps = {}) {
    this.baseUrl = deps.baseUrl ?? getApiBaseUrl();
    this.tokenManager = deps.tokenManager ?? getTokenManager();
    this.fetchImpl =
      deps.fetchImpl ??
      ((url, init) => fetch(url, init) as unknown as Promise<RawResponse>);
  }

  // ═════════════════════════════════════════════════════════
  // WORK ITEMS
  // ═════════════════════════════════════════════════════════

  /** GET /api/v1/workflows/work-items/mine — tasks assigned to the caller. */
  async listMyTasks(limit = 50): Promise<WorkItem[]> {
    return this.request<WorkItem[]>('GET', `/work-items/mine?limit=${limit}`);
  }

  /** GET /api/v1/workflows/work-items/pool — unassigned claimable tasks. */
  async listPool(limit = 50): Promise<WorkItem[]> {
    return this.request<WorkItem[]>('GET', `/work-items/pool?limit=${limit}`);
  }

  /** POST /api/v1/workflows/work-items/{id}/claim — expectedVersion from the loaded row. */
  async claimWorkItem(id: string, expectedVersion: number, reason = ''): Promise<WorkItem> {
    return this.request<WorkItem>('POST', `/work-items/${id}/claim`, {
      expectedVersion,
      reason,
    });
  }

  /** POST /api/v1/workflows/work-items/{id}/release. */
  async releaseWorkItem(id: string, expectedVersion: number, reason = ''): Promise<WorkItem> {
    return this.request<WorkItem>('POST', `/work-items/${id}/release`, {
      expectedVersion,
      reason,
    });
  }

  /** POST /api/v1/workflows/work-items/{id}/complete. */
  async completeWorkItem(id: string, expectedVersion: number, reason = ''): Promise<WorkItem> {
    return this.request<WorkItem>('POST', `/work-items/${id}/complete`, {
      expectedVersion,
      reason,
    });
  }

  // ═════════════════════════════════════════════════════════
  // APPROVALS
  // ═════════════════════════════════════════════════════════

  /** GET /api/v1/workflows/approvals/pending — decisions waiting on the caller. */
  async listPendingApprovals(limit = 50): Promise<ApprovalDecision[]> {
    return this.request<ApprovalDecision[]>('GET', `/approvals/pending?limit=${limit}`);
  }

  /** POST /api/v1/workflows/approvals/{id}/approve — expectedVersion from the loaded row. */
  async approveWorkItem(id: string, expectedVersion: number, comments = ''): Promise<ApprovalDecision> {
    return this.request<ApprovalDecision>('POST', `/approvals/${id}/approve`, {
      expectedVersion,
      comments,
    });
  }

  /** POST /api/v1/workflows/approvals/{id}/reject. */
  async rejectWorkItem(id: string, expectedVersion: number, comments = ''): Promise<ApprovalDecision> {
    return this.request<ApprovalDecision>('POST', `/approvals/${id}/reject`, {
      expectedVersion,
      comments,
    });
  }

  // ═════════════════════════════════════════════════════════
  // NOTIFICATIONS
  // ═════════════════════════════════════════════════════════

  /** GET /api/v1/workflows/notifications — newest first, per-user feed. */
  async listNotifications(limit = 50): Promise<WorkflowNotification[]> {
    return this.request<WorkflowNotification[]>('GET', `/notifications?limit=${limit}`);
  }

  /** GET /api/v1/workflows/notifications/unread-count → {unread: number}. */
  async unreadCount(): Promise<number> {
    const body = await this.request<{ unread?: number }>('GET', '/notifications/unread-count');
    return typeof body?.unread === 'number' ? body.unread : 0;
  }

  /** POST /api/v1/workflows/notifications/{id}/read — identity-checked server-side. */
  async markNotificationRead(id: string): Promise<void> {
    await this.request<unknown>('POST', `/notifications/${id}/read`);
  }

  /** POST /api/v1/workflows/notifications/read-all. */
  async markAllNotificationsRead(): Promise<void> {
    await this.request<unknown>('POST', '/notifications/read-all');
  }

  // ═════════════════════════════════════════════════════════
  // CORE REQUEST
  // ═════════════════════════════════════════════════════════

  private async request<T>(
    method: string,
    path: string,
    body?: unknown,
    allowAuthRetry = true
  ): Promise<T> {
    const accessToken = this.tokenManager.getAccessToken();
    let response: RawResponse;

    try {
      response = await this.fetchImpl(`${this.baseUrl}${WORKFLOWS_PATH}${path}`, {
        method,
        headers: {
          'Content-Type': 'application/json',
          ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch {
      throw new WorkflowApiError('NETWORK', 0, 'Network request failed.');
    }

    // 401 → single-flight refresh (shared with every other caller) + retry ONCE.
    if (response.status === 401) {
      if (allowAuthRetry) {
        const refreshed = await this.tokenManager.refreshSession();
        if (refreshed) {
          return this.request<T>(method, path, body, false);
        }
      }
      throw new WorkflowApiError(
        'UNAUTHENTICATED',
        401,
        'Session expired — sign in again.'
      );
    }

    if (response.status === 403) {
      throw new WorkflowApiError(
        'AUTHORIZATION_REVOKED',
        403,
        'Authorization revoked — the server refused this action for your account.'
      );
    }

    if (response.status === 409) {
      throw new WorkflowApiError(
        'STALE',
        409,
        'Stale — reload: this item changed on the server.'
      );
    }

    if (!response.ok) {
      throw new WorkflowApiError('HTTP', response.status, `Request failed (HTTP ${response.status}).`);
    }

    if (response.status === 204) {
      return undefined as T;
    }

    return (await response.json()) as T;
  }
}
