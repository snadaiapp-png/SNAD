/**
 * G8 Caller Identification — offline dataset sync client (G8-03 §37–§44).
 *
 * Cursor-based delta sync with idempotent upserts, tombstones, version gate
 * (mismatch ⇒ full rebuild), stale metadata, and corruption recovery
 * (invalid cursor ⇒ reset + FULL_RESYNC, never a permanent crash).
 * The HMAC dataset key is delivered once (SecureStore) — NEVER stored with
 * the dataset rows (G8-ADR-004 §34/§36).
 */

import * as SecureStore from 'expo-secure-store';
import { encryptField } from '../storage/encryption';
import { getSyncMetadata, setSyncMetadata, upsertCallerRecords } from '../storage/db';
import { ApiClient } from '../sync/api-client';
import { CALLER_DATASET_KEY_ALIAS, METADATA_LAST_SYNCED, purgeCallerDatasetRows } from './offline-lookup';
import { CallerDatasetDeltaResponse } from './types';

export const METADATA_VERSION = 'caller_dataset_version';
export const METADATA_CURSOR = 'caller_dataset_cursor';
export const METADATA_FULL_RESYNC = 'caller_dataset_full_resync_required';

export interface DatasetSyncResult {
  applied: number;
  tombstones: number;
  cursor: string | null;
  entries: number;
  fullResyncRequired: boolean;
}

/** SecureStore-backed dataset key (Keychain/Keystore — not with the rows). */
export async function getDatasetKey(): Promise<string | null> {
  return SecureStore.getItemAsync(CALLER_DATASET_KEY_ALIAS);
}

export async function storeDatasetKey(key: string): Promise<void> {
  await SecureStore.setItemAsync(CALLER_DATASET_KEY_ALIAS, key);
}

/**
 * Pull every pending delta page and apply it locally.
 *
 * @param tenantId authenticated tenant
 * @param keyMissing true on first sync (server issues the tenant dataset key)
 */
export async function syncCallerDataset(
  api: Pick<ApiClient, 'pullCallerDatasetDelta'>,
  tenantId: string,
  keyMissing: boolean
): Promise<DatasetSyncResult> {
  let applied = 0;
  let tombstones = 0;
  let fullResyncRequired = (await getSyncMetadata(METADATA_FULL_RESYNC)) === '1';
  let cursor = await getSyncMetadata(METADATA_CURSOR);
  let version = Number(await getSyncMetadata(METADATA_VERSION)) || 0;
  if (fullResyncRequired) {
    cursor = null;
  }

  let datasetKey = await getDatasetKey();
  if (datasetKey) keyMissing = false;

  for (let guard = 0; guard < 20; guard++) {
    let page: CallerDatasetDeltaResponse;
    try {
      page = await api.pullCallerDatasetDelta(cursor, 500, keyMissing && !datasetKey);
    } catch (error) {
      // Corruption of the cursor: recoverable — drop it and require a rebuild.
      if (isCursorError(error)) {
        await setSyncMetadata(METADATA_CURSOR, '');
        await setSyncMetadata(METADATA_FULL_RESYNC, '1');
        throw new Error('CALLER_DATASET_CORRUPT_CURSOR');
      }
      throw error;
    }
    if (page.datasetKey && !datasetKey) {
      await storeDatasetKey(page.datasetKey);
      datasetKey = page.datasetKey;
    }
    if (page.fullResyncRequired || (version !== 0 && page.datasetVersion !== version)) {
      // Dataset contract changed (or the server demands it) — rebuild from a
      // clean slate (§44). The first page ever received initializes `version`.
      await purgeCallerDatasetRows(tenantId);
      await setSyncMetadata(METADATA_CURSOR, '');
      cursor = null;
      version = page.datasetVersion;
      fullResyncRequired = false;
      await setSyncMetadata(METADATA_FULL_RESYNC, '0');
      continue;
    }

    const encrypted = [];
    for (const record of page.entries) {
      encrypted.push({
        lookupToken: record.lookupToken,
        entityType: record.entityType,
        entityId: record.entityId,
        // PII is encrypted at rest BEFORE the row is stored (§48).
        displayName: record.displayName == null ? null : await encryptField(record.displayName),
        accountId: record.accountId,
        accountName: record.accountName == null ? null : await encryptField(record.accountName),
        phoneLabel: record.phoneLabel,
        verified: record.verified,
        preferred: record.preferred,
        lifecycleStatus: record.lifecycleStatus,
        privacyLevel: record.privacyLevel,
        syncVersion: record.syncVersion,
        updatedAt: record.updatedAt,
        deleted: record.deleted,
      });
    }
    const outcome = await upsertCallerRecords(tenantId, encrypted);
    applied += outcome.applied;
    tombstones += outcome.tombstones;

    await setSyncMetadata(METADATA_VERSION, String(page.datasetVersion));
    await setSyncMetadata(METADATA_CURSOR, page.nextCursor ?? '');
    await setSyncMetadata(METADATA_LAST_SYNCED, new Date().toISOString());
    if (!page.hasMore) break;
    cursor = page.nextCursor;
  }
  await setSyncMetadata(METADATA_FULL_RESYNC, fullResyncRequired ? '1' : '0');
  return { applied, tombstones, cursor, entries: applied, fullResyncRequired };
}

/** Invalid/corrupt cursor from the server → treat as recoverable, rebuild. */
function isCursorError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error);
  return /cursor|400|422|invalid/i.test(message);
}
