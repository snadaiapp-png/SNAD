/**
 * G8 Caller Identification — offline dataset & lookup types.
 *
 * The offline caller dataset is a dedicated, minimum-PII projection
 * (G8-ADR-008 Option B): HMAC lookup tokens instead of plaintext numbers,
 * encrypted identity fields, tombstones, and cursor-based delta sync.
 */

/** One server-side dataset entry (G8-03 §39). */
export interface CallerDatasetRecord {
  lookupToken: string;
  entityType: string | null;
  entityId: string | null;
  displayName: string | null;
  accountId: string | null;
  accountName: string | null;
  phoneLabel: string | null;
  verified: boolean | null;
  preferred: boolean | null;
  lifecycleStatus: string | null;
  privacyLevel: string | null;
  syncVersion: number;
  updatedAt: string;
  deleted: boolean;
}

/** Delta sync response (G8-03 §37). */
export interface CallerDatasetDeltaResponse {
  datasetVersion: number;
  fullResyncRequired: boolean;
  nextCursor: string | null;
  hasMore: boolean;
  serverTimestamp: string;
  datasetKey: string | null;
  entries: CallerDatasetRecord[];
}

/** Offline caller lookup outcome (G8-03 §52–§53). */
export interface OfflineCallerLookupResult {
  offline: true;
  matchStatus:
    | 'EXACT'
    | 'AMBIGUOUS'
    | 'UNKNOWN'
    | 'RESTRICTED'
    | 'INVALID_NUMBER'
    | 'PRIVATE_NUMBER';
  candidateCount?: number;
  entityType?: string;
  entityId?: string;
  displayName?: string;
  accountId?: string;
  accountName?: string;
  phoneLabel?: string;
  verified?: boolean;
  preferred?: boolean;
  privacyLevel?: string;
  stale: boolean;
  /** Set when local corruption was detected → the caller should FULL_RESYNC. */
  fullResyncSuggested: boolean;
}
