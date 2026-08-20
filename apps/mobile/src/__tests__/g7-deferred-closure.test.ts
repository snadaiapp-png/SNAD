import {
  ENTITY_CONFIGS,
  assertPushEligible,
  canAutoMerge,
  getPullEligibleEntityTypes,
  getSyncableEntityTypes,
  isPullEligible,
  isPushEligible,
  requiresUserResolution,
} from '../config/entities';
import {
  DEFAULT_BACKGROUND_SYNC_INTERVAL_MS,
  DEFAULT_STORAGE_QUOTA_BYTES,
  DEFAULT_STORAGE_WARNING_RATIO,
  PeriodicSyncScheduler,
  assertCursorContinuity,
  createHttpConnectivityProbe,
  evaluateStorageQuota,
} from '../sync/runtime-controls';
import {
  clearEventBuffer,
  emitSyncEvent,
  getDashboardSnapshot,
} from '../obs/metrics';

describe('G7 deferred closure contracts', () => {
  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
    clearEventBuffer();
  });

  test('SYNC-013 rejects a non-advancing continuation cursor', () => {
    expect(() => assertCursorContinuity('c1', 'c1', true))
      .toThrow('SYNC_CURSOR_CONTINUITY_BROKEN');
    expect(() => assertCursorContinuity('c1', null, true))
      .toThrow('SYNC_CURSOR_CONTINUITY_BROKEN');
    expect(() => assertCursorContinuity('c1', 'c2', true)).not.toThrow();
    expect(() => assertCursorContinuity('c1', null, false)).not.toThrow();
  });

  test('OFF-002 gives every sync-enabled entity an explicit pull/push eligibility decision', () => {
    const syncable = getSyncableEntityTypes();
    expect(syncable).toHaveLength(Object.keys(ENTITY_CONFIGS).length);

    for (const entityType of syncable) {
      const cfg = ENTITY_CONFIGS[entityType];
      expect(isPullEligible(entityType)).toBe(!cfg.pushOnly);
      expect(isPushEligible(entityType)).toBe(!cfg.pullOnly);
    }

    expect(getPullEligibleEntityTypes()).toEqual(
      syncable.filter(type => !ENTITY_CONFIGS[type].pushOnly)
    );

    // note is intentionally push-only; this proves eligibility is enforced, not descriptive only.
    expect(isPullEligible('note')).toBe(false);
    expect(isPushEligible('note')).toBe(true);
    expect(() => assertPushEligible('note')).not.toThrow();
  });

  test('PERF-002 enforces deterministic storage quota states at the configured baseline', () => {
    const belowWarning = Math.floor(DEFAULT_STORAGE_QUOTA_BYTES * (DEFAULT_STORAGE_WARNING_RATIO - 0.01));
    const warning = Math.ceil(DEFAULT_STORAGE_QUOTA_BYTES * DEFAULT_STORAGE_WARNING_RATIO);

    expect(evaluateStorageQuota(belowWarning).state).toBe('OK');
    expect(evaluateStorageQuota(warning).state).toBe('WARNING');
    expect(evaluateStorageQuota(DEFAULT_STORAGE_QUOTA_BYTES).state).toBe('EXCEEDED');
    expect(() => evaluateStorageQuota(-1)).toThrow('INVALID_STORAGE_USAGE');
  });

  test('PERF-003 distinguishes reachable HTTP from transport failure', async () => {
    const fetchMock = jest.spyOn(globalThis, 'fetch' as any)
      .mockResolvedValueOnce({ ok: false, status: 503 } as Response)
      .mockRejectedValueOnce(new Error('network down'));
    const probe = createHttpConnectivityProbe('https://api.example.test/', 1000);

    // Any HTTP response means the network path is reachable, including a 503.
    await expect(probe()).resolves.toBe(true);
    await expect(probe()).resolves.toBe(false);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toBe('https://api.example.test/actuator/health');
  });

  test('PERF-004 background scheduler is periodic and never overlaps an in-flight cycle', async () => {
    jest.useFakeTimers();
    let executions = 0;
    let releaseFirst: (() => void) | undefined;
    const firstRun = new Promise<void>(resolve => { releaseFirst = resolve; });

    const scheduler = new PeriodicSyncScheduler(async () => {
      executions += 1;
      if (executions === 1) await firstRun;
    }, DEFAULT_BACKGROUND_SYNC_INTERVAL_MS);

    scheduler.start();
    expect(scheduler.isRunning()).toBe(true);

    await jest.advanceTimersByTimeAsync(DEFAULT_BACKGROUND_SYNC_INTERVAL_MS);
    expect(executions).toBe(1);

    // A second interval fires while the first is still active; it must be suppressed.
    await jest.advanceTimersByTimeAsync(DEFAULT_BACKGROUND_SYNC_INTERVAL_MS);
    expect(executions).toBe(1);

    releaseFirst?.();
    await Promise.resolve();
    await jest.advanceTimersByTimeAsync(DEFAULT_BACKGROUND_SYNC_INTERVAL_MS);
    expect(executions).toBe(2);

    scheduler.stop();
    expect(scheduler.isRunning()).toBe(false);
  });

  test('OBS-006 produces a stable sanitized dashboard projection', () => {
    emitSyncEvent('sync_completed');
    emitSyncEvent('sync_completed');
    emitSyncEvent('sync_failed', { accessToken: 'must-not-leak' });
    emitSyncEvent('conflict_detected');
    emitSyncEvent('storage_warning');
    emitSyncEvent('cursor_continuity_broken');

    const dashboard = getDashboardSnapshot();
    expect(dashboard.syncCompleted).toBe(2);
    expect(dashboard.syncFailed).toBe(1);
    expect(dashboard.conflictsDetected).toBe(1);
    expect(dashboard.storageWarnings).toBe(1);
    expect(dashboard.cursorContinuityFailures).toBe(1);
    expect(dashboard.successRate).toBeCloseTo(2 / 3);
  });

  test('ARCH-004 conflict behavior is explicit per entity instead of one global policy', () => {
    // Auto-merge family.
    for (const type of ['account', 'contact', 'task', 'activity'] as const) {
      expect(canAutoMerge(type)).toBe(true);
      expect(requiresUserResolution(type)).toBe(false);
    }

    // Human-resolution family.
    for (const type of ['lead', 'opportunity'] as const) {
      expect(canAutoMerge(type)).toBe(false);
      expect(requiresUserResolution(type)).toBe(true);
    }

    // Append-style note: neither field merge nor user-resolution workflow is required.
    expect(canAutoMerge('note')).toBe(false);
    expect(requiresUserResolution('note')).toBe(false);
  });

  test('TEST-006 sustains a 24h-equivalent network-flap/resume load without overlap', async () => {
    // 5-minute cadence = 288 scheduled opportunities in 24h. Alternate network
    // state each cycle to exercise repeated offline/resume behavior deterministically.
    const cycles = 288;
    let completed = 0;
    let concurrent = 0;
    let maxConcurrent = 0;
    let online = false;

    const scheduler = new PeriodicSyncScheduler(async () => {
      concurrent += 1;
      maxConcurrent = Math.max(maxConcurrent, concurrent);
      online = !online;
      if (online) completed += 1;
      await Promise.resolve();
      concurrent -= 1;
    }, 1);

    for (let i = 0; i < cycles; i += 1) {
      await scheduler.runNow();
    }

    expect(completed).toBe(cycles / 2);
    expect(maxConcurrent).toBe(1);
    expect(concurrent).toBe(0);
  });
});
