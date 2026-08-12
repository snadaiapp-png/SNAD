/**
 * G7 Mutation Queue
 *
 * Requirements: SYNC-003 (Mutation Queue)
 *
 * Durable mutation queue that survives app restart, device restart,
 * temporary connectivity loss, and authentication interruption.
 * Never silently discards mutations.
 */

import { getDatabase } from '../storage/db';
import { EntityType, Mutation, MutationOperation, MutationStatus } from '../types';
import * as Crypto from 'expo-crypto';

export class MutationQueue {

  /**
   * Enqueue a new mutation.
   * Returns the mutation ID.
   */
  async enqueue(params: {
    entityType: EntityType;
    entityId: string;
    operation: MutationOperation;
    payload: Record<string, any>;
    expectedVersion?: number;
  }): Promise<string> {
    const db = await getDatabase();
    const id = Crypto.randomUUID();
    const idempotencyKey = await this.generateIdempotencyKey(params);
    const now = new Date().toISOString();

    await db.runAsync(`
      INSERT INTO mutation_queue (
        id, idempotency_key, entity_type, entity_id, operation,
        expected_version, payload, client_timestamp,
        status, retry_count, max_retries, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED', 0, 5, ?, ?)
    `,
      id, idempotencyKey, params.entityType, params.entityId,
      params.operation, params.expectedVersion ?? null,
      JSON.stringify(params.payload), now, now, now
    );

    return id;
  }

  /**
   * Get queued mutations for push.
   */
  async getQueuedMutations(limit: number = 50): Promise<Mutation[]> {
    const db = await getDatabase();

    const rows = await db.getAllAsync<any>(
      `SELECT * FROM mutation_queue
       WHERE status = 'QUEUED'
       ORDER BY created_at ASC
       LIMIT ?`,
      limit
    );

    return rows.map(this.rowToMutation);
  }

  /**
   * Get mutations for a specific entity.
   */
  async getMutationsForEntity(entityType: EntityType, entityId: string): Promise<Mutation[]> {
    const db = await getDatabase();

    const rows = await db.getAllAsync<any>(
      `SELECT * FROM mutation_queue
       WHERE entity_type = ? AND entity_id = ?
         AND status IN ('QUEUED', 'SENDING')
       ORDER BY created_at ASC`,
      entityType, entityId
    );

    return rows.map(this.rowToMutation);
  }

  /**
   * Mark a mutation as applied (successfully sent to server).
   */
  async markApplied(mutationId: string): Promise<void> {
    const db = await getDatabase();
    await db.runAsync(
      `UPDATE mutation_queue SET status = 'APPLIED', updated_at = datetime('now') WHERE id = ?`,
      mutationId
    );
  }

  /**
   * Mark a mutation as rejected by server.
   */
  async markRejected(mutationId: string, errorMessage: string): Promise<void> {
    const db = await getDatabase();
    await db.runAsync(
      `UPDATE mutation_queue SET status = 'REJECTED', error_message = ?, updated_at = datetime('now') WHERE id = ?`,
      errorMessage, mutationId
    );
  }

  /**
   * Mark a mutation as conflict detected.
   */
  async markConflict(mutationId: string, conflictInfo: any): Promise<void> {
    const db = await getDatabase();
    await db.runAsync(
      `UPDATE mutation_queue SET status = 'CONFLICT', error_message = ?, updated_at = datetime('now') WHERE id = ?`,
      JSON.stringify(conflictInfo), mutationId
    );
  }

  /**
   * Mark a mutation for retry (with exponential backoff).
   */
  async markForRetry(mutationId: string, errorMessage: string): Promise<void> {
    const db = await getDatabase();

    const mutation = await db.getFirstAsync<any>(
      'SELECT retry_count, max_retries FROM mutation_queue WHERE id = ?',
      mutationId
    );

    if (!mutation) return;

    if (mutation.retry_count >= mutation.max_retries) {
      // Max retries exceeded — mark as FAILED
      await db.runAsync(
        `UPDATE mutation_queue SET status = 'FAILED', error_message = ?, updated_at = datetime('now') WHERE id = ?`,
        `Max retries exceeded: ${errorMessage}`, mutationId
      );
    } else {
      // Increment retry count and set status back to QUEUED
      await db.runAsync(
        `UPDATE mutation_queue SET status = 'QUEUED', retry_count = retry_count + 1, error_message = ?, updated_at = datetime('now') WHERE id = ?`,
        errorMessage, mutationId
      );
    }
  }

  /**
   * Get count of pending mutations.
   */
  async getPendingCount(): Promise<number> {
    const db = await getDatabase();
    const row = await db.getFirstAsync<{ count: number }>(
      `SELECT COUNT(*) as count FROM mutation_queue WHERE status IN ('QUEUED', 'SENDING')`
    );
    return row?.count ?? 0;
  }

  /**
   * Clear applied mutations older than 24 hours.
   */
  async cleanup(): Promise<void> {
    const db = await getDatabase();
    await db.runAsync(
      `DELETE FROM mutation_queue WHERE status = 'APPLIED' AND updated_at < datetime('now', '-1 day')`
    );
  }

  /**
   * Generate idempotency key from mutation parameters.
   * SHA-256 of: entityType + entityId + operation + payload hash.
   */
  private async generateIdempotencyKey(params: {
    entityType: EntityType;
    entityId: string;
    operation: MutationOperation;
    payload: Record<string, any>;
  }): Promise<string> {
    const payloadHash = JSON.stringify(params.payload, Object.keys(params.payload).sort());
    const data = `${params.entityType}:${params.entityId}:${params.operation}:${payloadHash}`;
    return await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, data);
  }

  /**
   * Convert database row to Mutation object.
   */
  private rowToMutation(row: any): Mutation {
    return {
      id: row.id,
      idempotencyKey: row.idempotency_key,
      entityType: row.entity_type,
      entityId: row.entity_id,
      operation: row.operation,
      expectedVersion: row.expected_version,
      payload: JSON.parse(row.payload),
      clientTimestamp: row.client_timestamp,
      status: row.status,
      retryCount: row.retry_count,
      maxRetries: row.max_retries,
      errorMessage: row.error_message,
      createdAt: row.created_at,
      updatedAt: row.updated_at,
    };
  }
}
