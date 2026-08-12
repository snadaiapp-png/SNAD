/**
 * G7 API Client
 *
 * Requirements: API-003 (Delta Sync Pull API), API-004 (Batch Sync Push API)
 *
 * HTTP client for communicating with the SNAD server sync endpoints.
 * Uses axios with ETag support and timeout handling.
 */

import { DeltaSyncResponse, PushSyncRequest, PushSyncResponse } from '../types';
import { SyncEngineConfig } from './sync-engine';

export class ApiClient {
  private baseUrl: string;
  private deviceId: string;
  private accessToken: string;
  private timeout: number;

  constructor(config: SyncEngineConfig) {
    this.baseUrl = config.apiBaseUrl;
    this.deviceId = config.deviceId;
    this.accessToken = config.accessToken;
    this.timeout = config.clientTimeoutMs;
  }

  /**
   * Delta sync pull — GET /api/v2/mobile/sync/pull
   */
  async pullDelta(
    entityType: string,
    cursor: string | null,
    limit: number
  ): Promise<DeltaSyncResponse> {
    const params = new URLSearchParams({
      entityType,
      limit: String(limit),
    });
    if (cursor) {
      params.set('cursor', cursor);
    }

    const response = await this.request('GET', `/api/v2/mobile/sync/pull?${params}`);

    return {
      entityType: response.entityType,
      nextCursor: response.nextCursor,
      entityCount: response.entityCount,
      entities: response.entities,
      serverTimestamp: response.serverTimestamp,
      hasMore: response.hasMore,
    };
  }

  /**
   * Batch sync push — POST /api/v2/mobile/sync/push
   */
  async pushBatch(request: PushSyncRequest): Promise<PushSyncResponse> {
    const response = await this.request('POST', '/api/v2/mobile/sync/push', request);

    return {
      totalMutations: response.totalMutations,
      applied: response.applied,
      rejected: response.rejected,
      duplicates: response.duplicates,
      results: response.results,
    };
  }

  /**
   * Get sync status — GET /api/v2/mobile/sync/status
   */
  async getSyncStatus(): Promise<any> {
    return this.request('GET', '/api/v2/mobile/sync/status');
  }

  /**
   * List conflicts — GET /api/v2/mobile/conflicts
   */
  async listConflicts(): Promise<any> {
    return this.request('GET', '/api/v2/mobile/conflicts');
  }

  /**
   * Resolve conflict — POST /api/v2/mobile/conflicts/:id/resolve
   */
  async resolveConflict(conflictId: string, resolution: string, resolutionData?: any): Promise<any> {
    return this.request('POST', `/api/v2/mobile/conflicts/${conflictId}/resolve`, {
      resolution,
      resolutionData,
    });
  }

  /**
   * Make an HTTP request.
   */
  private async request(method: string, path: string, body?: any): Promise<any> {
    const url = `${this.baseUrl}${path}`;

    const headers: Record<string, string> = {
      'Authorization': `Bearer ${this.accessToken}`,
      'X-Device-Id': this.deviceId,
      'Content-Type': 'application/json',
    };

    const fetchOptions: RequestInit = {
      method,
      headers,
      signal: AbortSignal.timeout(this.timeout),
    };

    if (body) {
      fetchOptions.body = JSON.stringify(body);
    }

    const response = await fetch(url, fetchOptions);

    if (!response.ok) {
      const error: any = new Error(`HTTP ${response.status}: ${response.statusText}`);
      error.statusCode = response.status;
      error.response = await response.json().catch(() => null);
      throw error;
    }

    return response.json();
  }
}

/**
 * Create an API client instance.
 */
export function getApiClient(config: SyncEngineConfig): ApiClient {
  return new ApiClient(config);
}
