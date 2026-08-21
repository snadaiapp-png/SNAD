/**
 * G8 Track E — Android native caller identification — shared types.
 *
 * The native module only exposes a bridge to the app; the ring-time path
 * itself (CallScreeningService) never touches JS or the network
 * (G8 EXECUTION 05 §3, §16).
 */

/** Role gate states (G8-05 §9). */
export type CallScreeningRoleState =
  | 'UNSUPPORTED'
  | 'AVAILABLE_NOT_GRANTED'
  | 'GRANTED'
  | 'REVOKED';

/** One native ring-time projection row fed from the Track D dataset. */
export interface NativeCallerProjectionRecord {
  lookupToken: string;
  entityType: string | null;
  entityId: string | null;
  displayName: string | null;
  accountName: string | null;
  phoneLabel: string | null;
  verified: boolean;
  preferred: boolean;
  lifecycleStatus: string;
  privacyLevel: string;
  syncVersion: number;
  updatedAt: string;
  deleted: boolean;
}

/** Status of the native ring-time projection (derived cache, not SSoT). */
export interface NativeCallerDatasetStatus {
  supported: boolean;
  provisioned: boolean;
  activeTenantId: string | null;
  datasetVersion: number;
  currentGeneration: number;
  entryCount: number;
  keyWrapped: boolean;
  stale: boolean;
  corrupt: boolean;
  fullResyncSuggested: boolean;
}

/** Result of purging the native projection (logout / tenant switch / revoke). */
export interface NativePurgeResult {
  purgedTenantId: string | null;
  rowsDeleted: number;
  keyAliasDeleted: boolean;
  observationsDeleted: number;
}

/** A RINGING observation queued natively for later JS flush (G8-05 §42). */
export interface NativeCallObservation {
  id: string;
  tenantId: string | null;
  status: 'RINGING';
  occurredAt: string;
}

export const CALL_SCREENING_MIN_API = 29;
export const NATIVE_STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000;

/** Native ring-time resolution states (G8-05 §34 — no first-row-wins). */
export type NativeMatchState =
  | 'EXACT'
  | 'AMBIGUOUS'
  | 'UNKNOWN'
  | 'RESTRICTED'
  | 'INVALID_NUMBER';
