/**
 * G8 Track E — Android native caller identification — TypeScript facade.
 *
 * The JS side NEVER receives onScreenCall callbacks: the native
 * CallScreeningService resolves the ring path entirely in Kotlin
 * (G8 EXECUTION 05 §3, §6). This facade is used by the app only for
 * provisioning, role management, dataset seeding and diagnostics.
 */

import { NativeModules, Platform } from 'react-native';
import type {
  CallScreeningRoleState,
  NativeCallObservation,
  NativeCallerDatasetStatus,
  NativeCallerProjectionRecord,
  NativePurgeResult,
} from './types';

const MODULE_NAME = 'SanadCallScreening';

interface NativeSanadCallScreening {
  isSupported(): boolean;
  isRoleAvailable(): boolean;
  isRoleHeld(): boolean;
  requestCallScreeningRole(): Promise<boolean>;
  getNativeCallerDatasetStatus(): Promise<NativeCallerDatasetStatus>;
  /** Seed / refresh the native ring-time projection from Track D deltas. */
  syncNativeCallerDataset(
    tenantId: string,
    datasetVersion: number,
    generation: number,
    datasetKey: string,
    records: NativeCallerProjectionRecord[]
  ): Promise<NativeCallerDatasetStatus>;
  purgeNativeCallerDataset(tenantId: string | null): Promise<NativePurgeResult>;
  takePendingCallObservations(): Promise<NativeCallObservation[]>;
  markObservationsFlushed(ids: string[]): Promise<void>;
}

function native(): NativeSanadCallScreening | null {
  if (Platform.OS !== 'android') return null;
  const mod = (NativeModules as Record<string, unknown>)[MODULE_NAME];
  return (mod as NativeSanadCallScreening | undefined) ?? null;
}

export function isSupported(): boolean {
  return native()?.isSupported() ?? false;
}

export function isRoleAvailable(): boolean {
  return native()?.isRoleAvailable() ?? false;
}

export function isRoleHeld(): boolean {
  return native()?.isRoleHeld() ?? false;
}

/** Opens the system role-manager consent screen. Resolves with the result. */
export async function requestCallScreeningRole(): Promise<boolean> {
  const mod = native();
  if (!mod) return false;
  return mod.requestCallScreeningRole();
}

export async function getNativeCallerDatasetStatus(): Promise<NativeCallerDatasetStatus | null> {
  return native()?.getNativeCallerDatasetStatus() ?? null;
}

export async function syncNativeCallerDataset(
  tenantId: string,
  datasetVersion: number,
  generation: number,
  datasetKey: string,
  records: NativeCallerProjectionRecord[]
): Promise<NativeCallerDatasetStatus | null> {
  const mod = native();
  if (!mod) return null;
  const batch = buildProjectionBatch(records);
  return mod.syncNativeCallerDataset(
    tenantId,
    datasetVersion,
    generation,
    datasetKey,
    batch
  );
}

export async function purgeNativeCallerDataset(
  tenantId: string | null
): Promise<NativePurgeResult | null> {
  return native()?.purgeNativeCallerDataset(tenantId) ?? null;
}

/** RINGING observations queued natively for the JS flush to Track C. */
export async function takePendingCallObservations(): Promise<NativeCallObservation[]> {
  return native()?.takePendingCallObservations() ?? [];
}

export async function markObservationsFlushed(ids: string[]): Promise<void> {
  await native()?.markObservationsFlushed(ids);
}

/** Role-state helper used by the app's setup UI (G8-05 §9, §45). */
export function roleState(supported: boolean, available: boolean, held: boolean): CallScreeningRoleState {
  if (!supported) return 'UNSUPPORTED';
  if (!available) return 'UNSUPPORTED';
  return held ? 'GRANTED' : 'REVOKED';
}

/**
 * Normalize Track D records for the native bridge: drop PII that the native
 * projection may not carry, coerce booleans, and cap the batch size.
 * Pure function (jest-testable without the native module).
 */
export function buildProjectionBatch(
  records: NativeCallerProjectionRecord[],
  maxBatch = 500
): NativeCallerProjectionRecord[] {
  return records.slice(0, maxBatch).map((r) => ({
    lookupToken: r.lookupToken,
    entityType: r.entityType ?? null,
    entityId: r.entityId ?? null,
    // Native layer encrypts these fields at rest (Keystore AES-GCM); RESTRICTED
    // rows never carry display PII server-side (G8-03 §41) — stripped here too.
    displayName: r.privacyLevel === 'RESTRICTED' ? null : (r.displayName ?? null),
    accountName: r.privacyLevel === 'RESTRICTED' ? null : (r.accountName ?? null),
    phoneLabel: r.phoneLabel ?? null,
    verified: Boolean(r.verified),
    preferred: Boolean(r.preferred),
    lifecycleStatus: r.lifecycleStatus ?? 'ACTIVE',
    privacyLevel: r.privacyLevel ?? 'PUBLIC',
    syncVersion: r.syncVersion ?? 0,
    updatedAt: r.updatedAt ?? '',
    deleted: Boolean(r.deleted),
  }));
}
