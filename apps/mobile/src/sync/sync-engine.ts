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
import { getDatabase, getSyncMetadata, setSyncMetadata, upsertEntity } from '../storage/db';
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
  pullLimit: number;          // max entities per pull request (default 100)
  pushBatchSize: number;      // max mutations per push request (default 50)
  clientTimeoutMs: number;    // timeout for sync operations (default 30000)
  retryDelayMs: number;       // initial retry delay (default 1000)
  maxRetries: number;         // max retry attempts (default 5)
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

  /**
   * Start the sync engine.
   */
  async start(): Promise<void> {
    this.updateState('ONLINE');
    await this.sync();
  }

  /**
   * Stop the sync engine.
   */
  stop(): void {
    if (this.syncInterval) {
      clearInterval(this.syncInterval);
      this.syncInterval = null;
    }
    this.updateState('OFFLINE');
  }

  /**
   * Perform a full sync cycle: pull then push.
   */
  async sync(): Promise<void> {
    if (this.state === 'REAUTH_REQUIRED' || this.state === 'SYNC_BLOCKED') {
      return;
    }

    try {
      // 1. PULL: Get changes from server
      await this.pullAll();

      // 2. PUSH: Send local mutations to server
      await this.pushAll();

      emitSyncEvent('sync_completed', { duration: 0 });
    } catch (error) {
      emitSyncEvent('sync_failed', { error: String(error) });

      if (this.isAuthError(error)) {
        this.updateState('REAUTH_REQUIRED');
      } else if (this.isNetworkError(error)) {
        this.updateState('OFFLINE');
      }
    }
  }

  /**
   * Pull changes for all entity types.
   * Each entity type is pulled independently — a conflict on one doesn't block others.
   */
  private async pullAll(): Promise<void> {
    const entityTypes = getSyncableEntityTypes();

    for (const entityType of entityTypes) {
      const config = ENTITY_CONFIGS[entityType];
      if (config.pushOnly) continue; // Skip push-only entities

      try {
        await this.pullEntity(entityType);
      } catch (error) {
        emitSyncEvent('pull_failed', { entityType, error: String(error) });
        // Continue with next entity type — don't let one failure block all
      }
    }
  }

  /**
   * Pull changes for a single entity type using delta sync.
   */
  private async pullEntity(entityType: EntityType): Promise<void> {
    const cursor = await getSyncMetadata(`cursor:${entityType}`);
    let hasMore = true;

    emitSyncEvent('pull_started', { entityType });

    while (hasMore) {
      const response: DeltaSyncResponse = await this.apiClient.pullDelta(
        entityType,
        cursor,
        this.config.pullLimit
      );

      // Process each entity in the delta
      for (const delta of response.entities) {
        await this.processDelta(entityType, delta);
      }

      // Save cursor for next pull
      if (response.nextCursor) {
        await setSyncMetadata(`cursor:${entityType}`, response.nextCursor);
      }

      hasMore = response.hasMore;
    }

    emitSyncEvent('pull_completed', { entityType, entityCount: 0 });
  }

  /**
   * Process a single entity delta from the server.
   */
  private async processDelta(entityType: EntityType, delta: any): Promise<void> {
    if (delta.operation === 'DELETE') {
      // Server deleted this entity — mark as deleted locally
      const { softDeleteEntity } = await import('../storage/db');
      await softDeleteEntity(entityType, delta.entityId);
      return;
    }

    // Check for local mutations on this entity
    const localMutations = await this.mutationQueue.getMutationsForEntity(entityType, delta.entityId);

    if (localMutations.length > 0) {
      // Conflict: server has changes AND client has pending mutations
      const conflict = await this.conflictResolver.detectConflict(
        entityType,
        delta.entityId,
        localMutations[0].expectedVersion ?? 0,
        localMutations[0].payload,
        delta.version,
        delta.data
      );

      if (conflict.canAutoMerge) {
        // Auto-merge non-conflicting fields
        const merged = await this.conflictResolver.autoMerge(entityType, localMutations[0].payload, delta.data);
        await upsertEntity(entityType, { ...merged, id: delta.entityId, sync_version: delta.version });

        // Mark local mutation as merged
        await this.mutationQueue.markApplied(localMutations[0].id);
      } else {
        // User resolution required — queue conflict
        await this.conflictResolver.queueConflict(conflict);

        // Save server state locally
        const decrypted = await decryptEntity(entityType, delta.data);
        await upsertEntity(entityType, { ...decrypted, id: delta.entityId, sync_version: delta.version });
      }
    } else {
      // No local conflict — just apply server data
      const decrypted = await decryptEntity(entityType, delta.data);
      await upsertEntity(entityType, { ...decrypted, id: delta.entityId, sync_version: delta.version });
    }
  }

  /**
   * Push all queued mutations to the server.
   */
  private async pushAll(): Promise<void> {
    const queuedMutations = await this.mutationQueue.getQueuedMutations(this.config.pushBatchSize);

    if (queuedMutations.length === 0) return;

    emitSyncEvent('push_started', { mutationCount: queuedMutations.length });

    // Encrypt sensitive fields before sending
    const encryptedMutations = await Promise.all(
      queuedMutations.map(async (m) => ({
        ...m,
        payload: await encryptEntity(m.entityType as EntityType, m.payload),
      }))
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

      // Process results
      for (const result of response.results) {
        const mutation = queuedMutations.find(m => m.idempotencyKey === result.idempotencyKey);
        if (!mutation) continue;

        switch (result.status) {
          case 'APPLIED':
            await this.mutationQueue.markApplied(mutation.id);
            emitSyncEvent('mutation_applied', { entityType: mutation.entityType });
            break;

          case 'CONFLICT':
            await this.mutationQueue.markConflict(mutation.id, result.conflictInfo);
            emitSyncEvent('conflict_detected', { entityType: mutation.entityType });
            break;

          case 'REJECTED':
            await this.mutationQueue.markRejected(mutation.id, result.errorMessage ?? 'Rejected');
            emitSyncEvent('mutation_rejected', { entityType: mutation.entityType, error: result.errorMessage });
            break;

          case 'DUPLICATE':
            await this.mutationQueue.markApplied(mutation.id);
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

      // Mark mutations for retry
      for (const mutation of queuedMutations) {
        await this.mutationQueue.markForRetry(mutation.id, String(error));
      }
    }
  }

  /**
   * Queue a local mutation for later sync.
   */
  async queueMutation(
    entityType: EntityType,
    entityId: string,
    operation: 'CREATE' | 'UPDATE' | 'DELETE',
    payload: Record<string, any>,
    expectedVersion?: number
  ): Promise<string> {
    const id = await this.mutationQueue.enqueue({
      entityType,
      entityId,
      operation,
      payload,
      expectedVersion,
    });

    emitSyncEvent('mutation_queued', { entityType, operation });

    return id;
  }

  /**
   * Get current sync state.
   */
  getState(): SyncState {
    return this.state;
  }

  /**
   * Update sync state and notify listeners.
   */
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
