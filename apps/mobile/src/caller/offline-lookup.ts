/**
 * G8 Caller Identification — offline local lookup (G8-03 §49, §52–§54).
 *
 * Chain: incoming phone → normalize (shared authority parity) → HMAC token →
 * indexed SQLite lookup → decrypt matched display fields only → result.
 * Corruption never crashes: it surfaces FULL_RESYNC suggestion (G8-03 §58).
 * Staleness is an indicator, never a hard disable (§54: STALE != DISABLED).
 */

import { decryptField } from '../storage/encryption';
import { countCallerEntries, findCallerRows, getSyncMetadata, setSyncMetadata } from '../storage/db';
import { hmacSha256Hex } from './hmac';
import { normalizePhone } from './normalizer';
import { OfflineCallerLookupResult } from './types';

const PRIVATE_SENTINELS = new Set([
  'PRIVATE', 'WITHHELD', 'BLOCKED', 'ANONYMOUS', 'UNKNOWN', 'PRIVATE_NUMBER',
]);

export const CALLER_DATASET_KEY_ALIAS = 'g8_caller_dataset_key_v1';
export const METADATA_LAST_SYNCED = 'caller_dataset_last_synced_at';
export const DEFAULT_STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000; // 24h

export interface OfflineLookupParams {
  tenantId: string;
  phone: string;
  countryHint?: string;
  /** HMAC dataset key (SecureStore-backed; null when not yet provisioned). */
  datasetKey?: string | null;
  staleThresholdMs?: number;
}

/** Tiered ranking mirroring the backend policy (§9 baseline). */
function tierOf(entityType: string, verified: boolean, preferred: boolean): number {
  if (entityType === 'CONTACT') return verified ? 0 : preferred ? 1 : 2;
  if (entityType === 'ACCOUNT') return 3;
  return 4;
}

/**
 * Resolve an incoming number against the LOCAL caller dataset.
 * Never touches the network — READ-ONLY.
 */
export async function offlineCallerLookup(params: OfflineLookupParams): Promise<OfflineCallerLookupResult> {
  const staleThresholdMs = params.staleThresholdMs ?? DEFAULT_STALE_THRESHOLD_MS;
  const stale = await isDatasetStale(staleThresholdMs);
  const base = { offline: true as const, stale, fullResyncSuggested: false };

  const raw = params.phone?.trim();
  if (raw == null || raw.length === 0 || PRIVATE_SENTINELS.has(raw.toUpperCase())) {
    return { ...base, matchStatus: raw == null || raw.length === 0 ? 'INVALID_NUMBER' : 'PRIVATE_NUMBER' };
  }

  const normalized = normalizePhone(raw, params.countryHint ?? 'SA');
  if (normalized == null) {
    return { ...base, matchStatus: 'INVALID_NUMBER' };
  }

  const key = params.datasetKey;
  if (!key) {
    // No HMAC key on device → nothing can be securely resolved.
    return { ...base, matchStatus: 'UNKNOWN', fullResyncSuggested: true };
  }

  const token = hmacSha256Hex(key, normalized);
  let rows: Array<Record<string, any>>;
  try {
    rows = await findCallerRows(params.tenantId, token);
  } catch (error) {
    // Corrupt local table/cursor → recoverable state, never a crash (§58).
    return { ...base, matchStatus: 'UNKNOWN', fullResyncSuggested: true };
  }
  if (rows.length === 0) {
    return { ...base, matchStatus: 'UNKNOWN' };
  }

  const decrypted: Array<Record<string, any>> = [];
  for (const row of rows) {
    try {
      decrypted.push({
        ...row,
        display_name: await decryptField(row.display_name ?? ''),
        account_name: await decryptField(row.account_name ?? ''),
      });
    } catch {
      return { ...base, matchStatus: 'UNKNOWN', fullResyncSuggested: true };
    }
  }

  // Winning tier = lowest rank; distinct identities decide EXACT vs AMBIGUOUS.
  let bestTier = Number.MAX_SAFE_INTEGER;
  for (const row of decrypted) {
    const tier = tierOf(row.entity_type, row.verified === 1, row.preferred === 1);
    if (tier < bestTier) bestTier = tier;
  }
  const winning = decrypted.filter(
    (row) => tierOf(row.entity_type, row.verified === 1, row.preferred === 1) === bestTier
  );
  const identities = new Set(winning.map((row) => `${row.entity_type}:${row.entity_id}`));

  if (identities.size > 1) {
    return { ...base, matchStatus: 'AMBIGUOUS', candidateCount: identities.size };
  }

  const winner = winning[0];
  if (winner.privacy_level === 'RESTRICTED') {
    return { ...base, matchStatus: 'RESTRICTED' };
  }
  return {
    ...base,
    matchStatus: 'EXACT',
    entityType: winner.entity_type,
    entityId: winner.entity_id,
    displayName: winner.display_name || undefined,
    accountId: winner.account_id || undefined,
    accountName: winner.account_name || undefined,
    phoneLabel: winner.phone_label || undefined,
    verified: winner.verified === 1,
    preferred: winner.preferred === 1,
    privacyLevel: winner.privacy_level,
  };
}

/** Dataset freshness (G8-03 §54): stale is an indicator, never a disable. */
export async function isDatasetStale(staleThresholdMs: number): Promise<boolean> {
  const last = await getSyncMetadata(METADATA_LAST_SYNCED);
  if (!last) return true;
  const lastMs = Date.parse(last);
  if (Number.isNaN(lastMs)) return true;
  return Date.now() - lastMs > staleThresholdMs;
}

/**
 * Purge the local dataset + HMAC key + metadata (G8-03 §55–§56): logout,
 * tenant switch, revocation, reinstall. The tenant-bound key is deleted in
 * EVERY case — a different tenant derives a different key, so dataset A can
 * never be searched from tenant B.
 */
export async function purgeCallerDataset(tenantId?: string): Promise<void> {
  await purgeCallerDatasetRows(tenantId);
  await setSyncMetadata(METADATA_LAST_SYNCED, '');
}

/** Row-level purge without touching the key (used by the sync client). */
export async function purgeCallerDatasetRows(tenantId?: string): Promise<void> {
  const { purgeCallerDataset: purgeRows } = await import('../storage/db');
  await purgeRows(tenantId);
  const { deleteItemAsync } = await import('expo-secure-store');
  await deleteItemAsync(CALLER_DATASET_KEY_ALIAS);
}

/** Dataset entry count (size visibility, G8-03 §59). */
export async function callerDatasetEntryCount(tenantId: string): Promise<number> {
  return countCallerEntries(tenantId);
}
