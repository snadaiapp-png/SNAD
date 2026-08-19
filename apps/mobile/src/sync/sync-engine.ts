/**
 * G7 Sync Engine
 *
 * Requirements: SYNC-001 (Sync Engine), SYNC-002 (Delta Pull), SYNC-014 (Client Timeout)
 *
 * Orchestrates pull and push synchronization.
 * Separate PULL from PUSH — a single conflict must NOT prevent unrelated entities from syncing.
 */

import { SyncState, EntityType, DeltaSyncResponse, PushSyncResponse } from '../types';
import { getSyncableEntityTypes, ENTITY_CONFIGS } from '../config/entities';
import { getSyncMetadata, setSyncMetadata, upsertEntity } from '../storage/db';
import { encryptEntity, decryptEntity } from '../storage/encryption';
import { MutationQueue } from './mutation-queue';
import { ConflictResolver } from '../conflict/resolver';
import { getApiClient, ApiClient } from './api-client';
import { emitSyncEvent } from '../obs/metrics';

export interface SyncEngineConfig {
  apiBaseUrl: string;
  deviceId: string;
  accessToken: string;
  tenantId: string;
  userId: string;
  pullLimit: number;
  pushBatchSize: number;
  clientTimeoutMs: number;
  retryDelayMs: number;
  maxRetries: number;
}

export class SyncEngine {
  private state: SyncState = 'OFFLINE';
  private config: SyncEngineConfig;
  private apiClient: ApiClient;
  private mutationQueue: MutationQueue;
  private conflictResolver: ConflictResolver;
  private syncInterval: ReturnType<typeof setInterval> | null = null;

  constructor(config: SyncEngineConfig) {
    this.config = config;
    this.apiClient = getApiClient(config);
    this.mutationQueue = new MutationQueue();
    this.conflictResolver = new ConflictResolver();
  }

  async start(): Promise<void> {
    this.updateState('ONLINE');
    await this.sync();
  }

  stop(): void {
    if (this.syncInterval) {
      clearInterval(this.syncInterval);
      this.syncInterval = null;
    }
    this.updateState('OFFLINE');
  }

  async sync(): Promise<void> {
    if (this.state === 'REAUTH_REQUIRED' || this.state === 'SYNC_BLOCKED') return;

    try {
      await this.pullAll();
      await this.pushAll();
      emitSyncEvent('sync_completed', { duration: 0 });
    } catch (error) {
      emitSyncEvent('sync_failed', { error: String(error) });
      if (this.isAuthError(error)) this.updateState('REAUTH_REQUIRED');
      else if (this.isNetworkError(error)) this.updateState('OFFLINE');
    }
  }

  private async pullAll(): Promise<void> {
    for (const entityType of getSyncableEntityTypes()) {
      const config = ENTITY_CONFIGS[entityType];
      if (config.pushOnly) continue;
      try {
        await this.pullEntity(entityType);
      } catch (error) {
        emitSyncEvent('pull_failed', { entityType, error: String(error) });
      }
    }
  }

  /** Pull every page exactly once, advancing the cursor in-memory before the next request. */
  private async pullEntity(entityType: EntityType): Promise<void> {
    let cursor = await getSyncMetadata(`cursor:${entityType}`);
    let hasMore = true;
    let entityCount = 0;

    emitSyncEvent('pull_started', { entityType });

    while (hasMore) {
      const response: DeltaSyncResponse = await this.apiClient.pullDelta(
        entityType,
        cursor,
        this.config.pullLimit
      );

      for (const delta of response.entities) {
        await this.processDelta(entityType, delta);
        entityCount++;
      }

      if (response.nextCursor) {
        cursor = response.nextCursor;
        await setSyncMetadata(`cursor:${entityType}`, cursor);
      } else if (response.hasMore) {
        throw new Error(`Invalid delta page for ${entityType}: hasMore=true without nextCursor`);
      }

      hasMore = response.hasMore;
    }

    emitSyncEvent('pull_completed', { entityType, entityCount });
  }

  private async processDelta(entityType: EntityType, delta: any): Promise<void> {
    if (delta.operation === 'DELETE') {
      const { softDeleteEntity } = await import('../storage/db');
      await softDeleteEntity(entityType, delta.entityId);
      return;
    }

    const localMutations = await this.mutationQueue.getMutationsForEntity(entityType, delta.entityId);

    if (localMutations.length > 0) {
      const conflict = await this.conflictResolver.detectConflict(
        entityType,
        delta.entityId,
        localMutations[0].expectedVersion ?? 0,
        localMutations[0].payload,
        delta.version,
        delta.data
      );

      if (conflict.canAutoMerge) {
        const merged = await this.conflictResolver.autoMerge(entityType, localMutations[0].payload, delta.data);
        await upsertEntity(entityType, { ...merged, id: delta.entityId, sync_version: delta.version });
        await this.mutationQueue.markApplied(localMutations[0].id);
      } else {
        await this.conflictResolver.queueConflict(conflict);
        const decrypted = await decryptEntity(entityType, delta.data);
        await upsertEntity(entityType, { ...decrypted, id: delta.entityId, sync_version: delta.version });
      }
    } else {
      const decrypted = await decryptEntity(entityType, delta.data);
      await upsertEntity(entityType, { ...decrypted, id: delta.entityId, sync_version: delta.version });
    }
  }

  private async pushAll(): Promise<void> {
    const queuedMutations = await this.mutationQueue.getQueuedMutations(this.config.pushBatchSize);
    if (queuedMutations.length === 0) return;

    emitSyncEvent('push_started', { mutationCount: queuedMutations.length });
    const encryptedMutations = await Promise.all(
      queuedMutations.map(async (m) => ({ ...m, payload: await encryptEntity(m.entityType as EntityType, m.payload) }))
    );

    try {
      const response: PushSyncResponse = await this.apiClient.pushBatch({
        mutations: encryptedMutations.map((m) => ({
          idempotencyKey: m.idempotencyKey,
          entityType: m.entityType,
          entityId: m.entityId,
          operation: m.operation,
          expectedVersion: m.expectedVersion,
          payload: m.payload,
        })),
      });

      for (const result of response.results) {
        const mutation = queuedMutations.find(m => m.idempotencyKey === result.idempotencyKey);
        if (!mutation) continue;
        switch (result.status) {
          case 'APPLIED':
          case 'DUPLICATE':
            await this.mutationQueue.markApplied(mutation.id);
            break;
          case 'CONFLICT':
            await this.mutationQueue.markConflict(mutation.id, result.conflictInfo);
            emitSyncEvent('conflict_detected', { entityType: mutation.entityType });
            break;
          case 'REJECTED':
            await this.mutationQueue.markRejected(mutation.id, result.errorMessage ?? 'Rejected');
            emitSyncEvent('mutation_rejected', { entityType: mutation.entityType, error: result.errorMessage });
            break;
        }
      }

      emitSyncEvent('push_completed', {
        applied: response.applied,
        rejected: response.rejected,
        duplicates: response.duplicates,
      });
    } catch (error) {
      emitSyncEvent('push_failed', { error: String(error) });
      for (const mutation of queuedMutations) {
        await this.mutationQueue.markForRetry(mutation.id, String(error));
      }
    }
  }

  async queueMutation(
    entityType: EntityType,
    entityId: string,
    operation: 'CREATE' | 'UPDATE' | 'DELETE',
    payload: Record<string, any>,
    expectedVersion?: number
  ): Promise<string> {
    const id = await this.mutationQueue.enqueue({ entityType, entityId, operation, payload, expectedVersion });
    emitSyncEvent('mutation_queued', { entityType, operation });
    return id;
  }

  getState(): SyncState { return this.state; }

  private updateState(newState: SyncState): void {
    const oldState = this.state;
    this.state = newState;
    emitSyncEvent('state_changed', { from: oldState, to: newState });
  }

  private isAuthError(error: any): boolean {
    return error?.statusCode === 401 || error?.statusCode === 403;
  }

  private isNetworkError(error: any): boolean {
    return error?.code === 'NETWORK_ERROR' || error?.message?.includes('Network');
  }
}
