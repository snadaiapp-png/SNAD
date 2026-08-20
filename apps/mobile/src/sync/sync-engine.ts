/**
 * G7 Sync Engine
 * Requirements: SYNC-001/002/013/014, OFF-002, PERF-002/003/004.
 */

import { SyncState, EntityType, DeltaSyncResponse, PushSyncResponse } from '../types';
import { getPullEligibleEntityTypes, assertPushEligible } from '../config/entities';
import { getSyncMetadata, setSyncMetadata, upsertEntity } from '../storage/db';
import { getLocalDatabaseUsageBytes } from '../storage/quota';
import { encryptEntity, decryptEntity } from '../storage/encryption';
import { MutationQueue } from './mutation-queue';
import { ConflictResolver } from '../conflict/resolver';
import { getApiClient, ApiClient } from './api-client';
import { emitSyncEvent } from '../obs/metrics';
import {
  assertCursorContinuity,
  ConnectivityProbe,
  createHttpConnectivityProbe,
  DEFAULT_BACKGROUND_SYNC_INTERVAL_MS,
  DEFAULT_STORAGE_QUOTA_BYTES,
  evaluateStorageQuota,
  PeriodicSyncScheduler,
} from './runtime-controls';

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
  storageQuotaBytes?: number;
  backgroundSyncIntervalMs?: number;
  connectivityProbe?: ConnectivityProbe;
}

export class SyncEngine {
  private state: SyncState = 'OFFLINE';
  private readonly apiClient: ApiClient;
  private readonly mutationQueue = new MutationQueue();
  private readonly conflictResolver = new ConflictResolver();
  private readonly connectivityProbe: ConnectivityProbe;
  private readonly scheduler: PeriodicSyncScheduler;

  constructor(private readonly config: SyncEngineConfig) {
    this.apiClient = getApiClient(config);
    this.connectivityProbe = config.connectivityProbe
      ?? createHttpConnectivityProbe(config.apiBaseUrl, Math.min(config.clientTimeoutMs, 5_000));
    this.scheduler = new PeriodicSyncScheduler(
      () => this.sync(),
      config.backgroundSyncIntervalMs ?? DEFAULT_BACKGROUND_SYNC_INTERVAL_MS
    );
  }

  async start(): Promise<void> {
    this.updateState('ONLINE');
    await this.sync();
    this.scheduler.start();
    emitSyncEvent('background_sync_started', {
      intervalMs: this.config.backgroundSyncIntervalMs ?? DEFAULT_BACKGROUND_SYNC_INTERVAL_MS,
    });
  }

  stop(): void {
    this.scheduler.stop();
    emitSyncEvent('background_sync_stopped');
    this.updateState('OFFLINE');
  }

  async sync(): Promise<void> {
    if (this.state === 'REAUTH_REQUIRED' || this.state === 'SYNC_BLOCKED' || this.state === 'FULL_RESYNC_REQUIRED') return;

    emitSyncEvent('sync_started');
    try {
      if (!(await this.connectivityProbe())) {
        emitSyncEvent('network_offline');
        this.updateState('OFFLINE');
        return;
      }
      if (this.state === 'OFFLINE') this.updateState('ONLINE');

      const storage = evaluateStorageQuota(
        await getLocalDatabaseUsageBytes(),
        this.config.storageQuotaBytes ?? DEFAULT_STORAGE_QUOTA_BYTES
      );
      if (storage.state === 'WARNING') {
        emitSyncEvent('storage_warning', { usageRatio: storage.usageRatio, usageBytes: storage.usageBytes });
      }

      // Preserve outbound data even if local cache is full; only additional pulls are blocked.
      if (storage.state === 'EXCEEDED') {
        emitSyncEvent('storage_quota_exceeded', { usageRatio: storage.usageRatio, usageBytes: storage.usageBytes });
      } else {
        await this.pullAll();
      }

      await this.pushAll();
      emitSyncEvent('sync_completed');
    } catch (error) {
      emitSyncEvent('sync_failed', { error: String(error) });
      if (this.state === 'FULL_RESYNC_REQUIRED') return;
      if (this.isAuthError(error)) this.updateState('REAUTH_REQUIRED');
      else if (this.isNetworkError(error)) this.updateState('OFFLINE');
    }
  }

  private async pullAll(): Promise<void> {
    for (const entityType of getPullEligibleEntityTypes()) {
      try {
        await this.pullEntity(entityType);
      } catch (error) {
        emitSyncEvent('pull_failed', { entityType, error: String(error) });
        if (this.state === 'FULL_RESYNC_REQUIRED') throw error;
      }
    }
  }

  private async pullEntity(entityType: EntityType): Promise<void> {
    let cursor = await getSyncMetadata(`cursor:${entityType}`);
    let hasMore = true;
    let entityCount = 0;
    emitSyncEvent('pull_started', { entityType });

    while (hasMore) {
      const response: DeltaSyncResponse = await this.apiClient.pullDelta(entityType, cursor, this.config.pullLimit);
      for (const delta of response.entities) {
        await this.processDelta(entityType, delta);
        entityCount += 1;
      }

      try {
        assertCursorContinuity(cursor, response.nextCursor, response.hasMore);
      } catch (error) {
        emitSyncEvent('cursor_continuity_broken', { entityType });
        this.updateState('FULL_RESYNC_REQUIRED');
        throw error;
      }

      if (response.nextCursor) {
        cursor = response.nextCursor;
        await setSyncMetadata(`cursor:${entityType}`, response.nextCursor);
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
        entityType, delta.entityId, localMutations[0].expectedVersion ?? 0,
        localMutations[0].payload, delta.version, delta.data
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

    const encryptedMutations = await Promise.all(queuedMutations.map(async m => ({
      ...m, payload: await encryptEntity(m.entityType as EntityType, m.payload),
    })));

    try {
      const response: PushSyncResponse = await this.apiClient.pushBatch({
        mutations: encryptedMutations.map(m => ({
          idempotencyKey: m.idempotencyKey, entityType: m.entityType,
          entityId: m.entityId, operation: m.operation,
          expectedVersion: m.expectedVersion, payload: m.payload,
        })),
      });

      for (const result of response.results) {
        const mutation = queuedMutations.find(m => m.idempotencyKey === result.idempotencyKey);
        if (!mutation) continue;
        switch (result.status) {
          case 'APPLIED':
          case 'DUPLICATE':
            await this.mutationQueue.markApplied(mutation.id);
            if (result.status === 'APPLIED') emitSyncEvent('mutation_applied', { entityType: mutation.entityType });
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
      emitSyncEvent('push_completed', { applied: response.applied, rejected: response.rejected, duplicates: response.duplicates });
    } catch (error) {
      emitSyncEvent('push_failed', { error: String(error) });
      for (const mutation of queuedMutations) await this.mutationQueue.markForRetry(mutation.id, String(error));
    }
  }

  async queueMutation(
    entityType: EntityType,
    entityId: string,
    operation: 'CREATE' | 'UPDATE' | 'DELETE',
    payload: Record<string, any>,
    expectedVersion?: number
  ): Promise<string> {
    assertPushEligible(entityType);
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
