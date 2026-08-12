/**
 * G7 Conflict Resolver
 *
 * Requirements: ARCH-002 (12 Conflict Classes), SYNC-005 (Conflict Detection),
 *               SYNC-006 (Conflict Resolution), SYNC-009 (Conflict Isolation)
 *
 * Implements conflict detection, classification, auto-merge, and user resolution
 * per ADR-G7-001 Hybrid Policy.
 */

import { Conflict, ConflictClass, EntityType } from '../types';
import { canAutoMerge, requiresUserResolution, ENTITY_CONFIGS } from '../config/entities';
import { getDatabase } from '../storage/db';
import * as Crypto from 'expo-crypto';

export class ConflictResolver {

  /**
   * Detect and classify a conflict between client and server state.
   *
   * Conflict Classes (from ADR-G7-001):
   *   C1: Same Record / Same Field → User Resolution
   *   C2: Same Record / Different Fields → Auto-Merge (if allowed)
   *   C3: Delete vs Update → User Resolution
   *   C4: Update vs Delete → User Resolution
   *   C5: State Transition Conflict → User Resolution
   *   C6: Ownership Conflict → Server Authority
   *   C7: Non-Overlapping Fields → Auto-Merge (if allowed)
   *   C8: Concurrent Creates → User Resolution
   *   C9: Stale Read Conflict → User Resolution
   *   C10: Cross-Tenant Attempt → Server Rejects
   *   C11: Batch Partial Failure → Per-Mutation Handling
   *   C12: Append-Only Conflict → Push-Only (Archive)
   */
  async detectConflict(
    entityType: EntityType,
    entityId: string,
    clientVersion: number,
    clientPayload: Record<string, any>,
    serverVersion: number,
    serverPayload: Record<string, any>
  ): Promise<Conflict & { canAutoMerge: boolean }> {
    let conflictClass: ConflictClass;
    let conflictType: string;
    let canAutoMergeFlag = false;

    // Determine conflict class
    if (clientVersion < serverVersion) {
      // Client has stale data
      if (this.hasFieldOverlap(clientPayload, serverPayload)) {
        conflictClass = 'C1';
        conflictType = 'SAME_FIELD_BOTH_SIDES';
      } else {
        conflictClass = 'C2';
        conflictType = 'NON_OVERLAPPING_FIELDS';
        canAutoMergeFlag = canAutoMerge(entityType);
      }
    } else if (clientVersion === serverVersion) {
      // Same version — concurrent modification
      if (this.hasFieldOverlap(clientPayload, serverPayload)) {
        conflictClass = 'C1';
        conflictType = 'FIELD_CONFLICT';
      } else {
        conflictClass = 'C7';
        conflictType = 'NON_OVERLAPPING_FIELDS';
        canAutoMergeFlag = canAutoMerge(entityType);
      }
    } else {
      conflictClass = 'C9';
      conflictType = 'STALE_READ';
    }

    const conflict: Conflict = {
      conflictId: Crypto.randomUUID(),
      entityType,
      entityId,
      conflictType,
      conflictClass,
      status: 'OPEN',
      clientVersion,
      serverVersion,
      clientState: clientPayload,
      serverState: serverPayload,
      detectedAt: new Date().toISOString(),
    };

    return { ...conflict, canAutoMerge: canAutoMergeFlag };
  }

  /**
   * Auto-merge non-conflicting fields.
   * Only permitted for: Account, Contact, Task, Activity (per ADR-G7-001).
   */
  async autoMerge(
    entityType: EntityType,
    clientPayload: Record<string, any>,
    serverPayload: Record<string, any>
  ): Promise<Record<string, any>> {
    if (!canAutoMerge(entityType)) {
      throw new Error(`Auto-merge not permitted for entity type: ${entityType}`);
    }

    const merged = { ...serverPayload };

    for (const [key, value] of Object.entries(clientPayload)) {
      // Only merge fields that are NOT in the server payload
      // or where server has null/undefined
      if (!(key in merged) || merged[key] === null || merged[key] === undefined) {
        merged[key] = value;
      }
    }

    return merged;
  }

  /**
   * Queue a conflict for user resolution.
   */
  async queueConflict(conflict: Conflict): Promise<void> {
    const db = await getDatabase();

    await db.runAsync(`
      INSERT INTO conflict_queue (
        id, entity_type, entity_id, conflict_class, conflict_type,
        client_version, server_version, client_state, server_state,
        status, detected_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?)
    `,
      conflict.conflictId,
      conflict.entityType,
      conflict.entityId,
      conflict.conflictClass,
      conflict.conflictType,
      conflict.clientVersion,
      conflict.serverVersion,
      JSON.stringify(conflict.clientState),
      JSON.stringify(conflict.serverState),
      conflict.detectedAt
    );
  }

  /**
   * Resolve a conflict with user choice.
   */
  async resolveConflict(
    conflictId: string,
    resolution: 'CLIENT_WINS' | 'SERVER_WINS' | 'MERGED' | 'USER_CHOICE'
  ): Promise<void> {
    const db = await getDatabase();

    await db.runAsync(`
      UPDATE conflict_queue
      SET status = 'RESOLVED',
          resolution = ?,
          resolved_at = datetime('now')
      WHERE id = ? AND status = 'OPEN'
    `, resolution, conflictId);
  }

  /**
   * Get all open conflicts.
   */
  async getOpenConflicts(): Promise<Conflict[]> {
    const db = await getDatabase();

    const rows = await db.getAllAsync<any>(
      `SELECT * FROM conflict_queue WHERE status = 'OPEN' ORDER BY detected_at ASC`
    );

    return rows.map(row => ({
      conflictId: row.id,
      entityType: row.entity_type as EntityType,
      entityId: row.entity_id,
      conflictType: row.conflict_type,
      conflictClass: row.conflict_class as ConflictClass,
      status: row.status,
      clientVersion: row.client_version,
      serverVersion: row.server_version,
      clientState: JSON.parse(row.client_state),
      serverState: JSON.parse(row.server_state),
      detectedAt: row.detected_at,
    }));
  }

  /**
   * Get conflict count by entity type.
   */
  async getConflictCounts(): Promise<Record<string, number>> {
    const db = await getDatabase();

    const rows = await db.getAllAsync<any>(
      `SELECT entity_type, COUNT(*) as count FROM conflict_queue WHERE status = 'OPEN' GROUP BY entity_type`
    );

    const counts: Record<string, number> = {};
    for (const row of rows) {
      counts[row.entity_type] = row.count;
    }
    return counts;
  }

  /**
   * Check if client and server payloads overlap on any fields.
   */
  private hasFieldOverlap(
    client: Record<string, any>,
    server: Record<string, any>
  ): boolean {
    for (const key of Object.keys(client)) {
      if (key in server && JSON.stringify(client[key]) !== JSON.stringify(server[key])) {
        return true;
      }
    }
    return false;
  }
}
