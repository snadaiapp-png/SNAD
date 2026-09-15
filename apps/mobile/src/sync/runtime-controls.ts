/**
 * G7 full-closure runtime controls.
 *
 * Requirements:
 * - SYNC-013 sequence/cursor continuity detection
 * - PERF-002 local storage quota policy
 * - PERF-003 network detection
 * - PERF-004 periodic background sync scheduling while the JS runtime is active
 */

export const DEFAULT_STORAGE_QUOTA_BYTES = 256 * 1024 * 1024; // 256 MiB
export const DEFAULT_STORAGE_WARNING_RATIO = 0.90;
export const DEFAULT_BACKGROUND_SYNC_INTERVAL_MS = 5 * 60 * 1000;

export type StorageQuotaState = 'OK' | 'WARNING' | 'EXCEEDED';

export interface StorageQuotaEvaluation {
  state: StorageQuotaState;
  usageBytes: number;
  quotaBytes: number;
  usageRatio: number;
}

export function evaluateStorageQuota(
  usageBytes: number,
  quotaBytes: number = DEFAULT_STORAGE_QUOTA_BYTES,
  warningRatio: number = DEFAULT_STORAGE_WARNING_RATIO
): StorageQuotaEvaluation {
  if (!Number.isFinite(usageBytes) || usageBytes < 0) {
    throw new Error('INVALID_STORAGE_USAGE');
  }
  if (!Number.isFinite(quotaBytes) || quotaBytes <= 0) {
    throw new Error('INVALID_STORAGE_QUOTA');
  }
  if (warningRatio <= 0 || warningRatio >= 1) {
    throw new Error('INVALID_STORAGE_WARNING_RATIO');
  }

  const usageRatio = usageBytes / quotaBytes;
  const state: StorageQuotaState = usageRatio >= 1
    ? 'EXCEEDED'
    : usageRatio >= warningRatio
      ? 'WARNING'
      : 'OK';

  return { state, usageBytes, quotaBytes, usageRatio };
}

/**
 * SYNC-013 invariant: when the server says another page exists, the continuation
 * cursor must be present and must advance. sync_version is not assumed to be a
 * globally contiguous integer; that would create false positive gaps across rows.
 */
export function assertCursorContinuity(
  currentCursor: string | null,
  nextCursor: string | null,
  hasMore: boolean
): void {
  if (!hasMore) return;
  if (!nextCursor || nextCursor === currentCursor) {
    throw new Error('SYNC_CURSOR_CONTINUITY_BROKEN');
  }
}

export type ConnectivityProbe = () => Promise<boolean>;

/**
 * Lightweight connectivity check. Any HTTP response proves network reachability;
 * only transport failure/timeout is considered offline.
 */
export function createHttpConnectivityProbe(
  apiBaseUrl: string,
  timeoutMs: number = 5_000
): ConnectivityProbe {
  return async () => {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), timeoutMs);
    try {
      await fetch(`${apiBaseUrl.replace(/\/$/, '')}/actuator/health`, {
        method: 'HEAD',
        signal: controller.signal,
      });
      return true;
    } catch {
      return false;
    } finally {
      clearTimeout(timeout);
    }
  };
}

/**
 * A non-overlapping periodic scheduler. On native platforms the OS may suspend
 * the JS runtime; resume/start hooks can invoke runNow() immediately. This class
 * guarantees no concurrent sync cycle while the runtime is active.
 */
export class PeriodicSyncScheduler {
  private timer: ReturnType<typeof setInterval> | null = null;
  private inFlight = false;

  constructor(
    private readonly task: () => Promise<void>,
    private readonly intervalMs: number = DEFAULT_BACKGROUND_SYNC_INTERVAL_MS
  ) {
    if (!Number.isFinite(intervalMs) || intervalMs <= 0) {
      throw new Error('INVALID_BACKGROUND_SYNC_INTERVAL');
    }
  }

  start(): void {
    if (this.timer) return;
    this.timer = setInterval(() => {
      void this.runNow();
    }, this.intervalMs);
  }

  stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  async runNow(): Promise<void> {
    if (this.inFlight) return;
    this.inFlight = true;
    try {
      await this.task();
    } finally {
      this.inFlight = false;
    }
  }

  isRunning(): boolean {
    return this.timer !== null;
  }
}
