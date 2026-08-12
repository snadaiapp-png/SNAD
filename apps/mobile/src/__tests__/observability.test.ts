/**
 * G7 Test Suite — Observability & Metrics
 * Tests: Sync event emission, sanitization, summary
 *
 * All tests use the actual metrics.ts API signatures.
 */

import { emitSyncEvent, getRecentEvents, getEventSummary, clearEventBuffer } from '../obs/metrics';
import {
  recordCrash,
  getCrashReports,
  getCrashCount,
  clearCrashReports,
  setCrashReporterSink,
  installCrashReporter,
} from '../obs/crash-reporter';
import {
  evaluateAlerts,
  raiseAlerts,
  resetAlertState,
  DEFAULT_THRESHOLDS,
} from '../obs/alerts';

// ═══════════════════════════════════════════════════════════
// TEST 20: Sync Event Metrics
// ═══════════════════════════════════════════════════════════
describe('TEST-20: Sync Event Metrics', () => {
  beforeEach(() => {
    clearEventBuffer();
  });

  test('sync pull event emitted with correct type', () => {
    emitSyncEvent('pull_completed', {
      entityType: 'accounts',
      entitiesReceived: 15,
      cursorUsed: true,
      durationMs: 250,
    });

    const events = getRecentEvents();
    expect(events.length).toBe(1);
    expect(events[0].type).toBe('pull_completed');
    expect(events[0].data?.entityType).toBe('accounts');
    expect(events[0].data?.entitiesReceived).toBe(15);
  });

  test('sync push event emitted with result counts', () => {
    emitSyncEvent('push_completed', {
      entityType: 'contacts',
      mutationsSent: 5,
      applied: 3,
      conflicts: 1,
      rejected: 1,
      durationMs: 180,
    });

    const events = getRecentEvents();
    expect(events.length).toBe(1);
    expect(events[0].type).toBe('push_completed');
    expect(events[0].data?.applied).toBe(3);
  });

  test('conflict event emitted', () => {
    emitSyncEvent('conflict_detected', {
      entityType: 'opportunities',
      conflictType: 'C1',
      requiresUserResolution: true,
    });

    const events = getRecentEvents();
    expect(events.length).toBe(1);
    expect(events[0].type).toBe('conflict_detected');
  });

  test('event summary aggregates correctly', () => {
    emitSyncEvent('pull_completed');
    emitSyncEvent('pull_completed');
    emitSyncEvent('push_completed');
    emitSyncEvent('conflict_detected');

    const summary = getEventSummary();
    expect(summary.pull_completed).toBe(2);
    expect(summary.push_completed).toBe(1);
    expect(summary.conflict_detected).toBe(1);
  });

  test('sensitive data is sanitized from metrics', () => {
    emitSyncEvent('sync_started', {
      name: 'John Doe',
      email: 'john@example.com',
      ssn: '123-45-6789',
      password: 'secret123',
      accessToken: 'token-abc',
    });

    const events = getRecentEvents();
    expect(events[0].data?.name).toBe('John Doe');
    expect(events[0].data?.email).toBe('[REDACTED]');
    expect(events[0].data?.ssn).toBe('[REDACTED]');
    expect(events[0].data?.password).toBe('[REDACTED]');
    expect(events[0].data?.accessToken).toBe('[REDACTED]');
  });

  test('getRecentEvents respects count parameter', () => {
    for (let i = 0; i < 10; i++) {
      emitSyncEvent('sync_started');
    }

    const recent = getRecentEvents(3);
    expect(recent.length).toBe(3);
  });

  test('event buffer trims at max size', () => {
    // Emit more than MAX_BUFFER_SIZE (1000)
    for (let i = 0; i < 1002; i++) {
      emitSyncEvent('sync_started');
    }

    const events = getRecentEvents(2000);
    expect(events.length).toBeLessThanOrEqual(1000);
  });
});

// ═══════════════════════════════════════════════════════════
// OBS-003: Crash Reporting
// ═══════════════════════════════════════════════════════════
describe('OBS-003: Crash Reporting', () => {
  beforeEach(() => {
    clearCrashReports();
    clearEventBuffer();
    setCrashReporterSink(null);
  });

  test('recordCrash captures message, stack, and marks unhandled', () => {
    const report = recordCrash(new Error('boom'), { screen: 'Home' });
    expect(report.message).toBe('boom');
    expect(report.handled).toBe(false);
    expect(report.stack).toContain('boom');
    expect(report.context?.screen).toBe('Home');
    expect(getCrashCount()).toBe(1);
  });

  test('non-Error values are normalized into a CrashReport', () => {
    const report = recordCrash('string failure');
    expect(report.message).toBe('string failure');
    expect(getCrashCount()).toBe(1);
  });

  test('sensitive context fields are redacted from the crash report', () => {
    const report = recordCrash(new Error('x'), { accessToken: 'tkn', password: 'p', email: 'a@b.c' });
    expect(report.context?.accessToken).toBe('[REDACTED]');
    expect(report.context?.password).toBe('[REDACTED]');
    expect(report.context?.email).toBe('[REDACTED]');
  });

  test('crashes surface into the sync metrics event stream', () => {
    recordCrash(new Error('network down'));
    const events = getRecentEvents();
    expect(events.some(e => e.type === 'sync_failed' && e.data?.crash === 'network down')).toBe(true);
  });

  test('pluggable sink receives the report; sink failures are swallowed', () => {
    const received: any[] = [];
    setCrashReporterSink(r => received.push(r));
    recordCrash(new Error('sink-test'));
    expect(received.length).toBe(1);
    expect(received[0].message).toBe('sink-test');

    // A throwing sink must not propagate.
    setCrashReporterSink(() => { throw new Error('sink broke'); });
    expect(() => recordCrash(new Error('after-bad-sink'))).not.toThrow();
  });

  test('crash buffer is bounded (trims to max)', () => {
    for (let i = 0; i < 250; i++) recordCrash(new Error(`e${i}`));
    // MAX_CRASH_BUFFER = 200
    expect(getCrashCount()).toBeLessThanOrEqual(200);
    const recent = getCrashReports(10);
    expect(recent[recent.length - 1].message).toBe('e249');
  });

  test('installCrashReporter is a no-op outside React Native (returns false, does not throw)', () => {
    expect(installCrashReporter()).toBe(false);
    expect(() => installCrashReporter()).not.toThrow();
  });

  test('installCrashReporter wires ErrorUtils.setGlobalHandler when present', () => {
    const calls: any[] = [];
    const fakeErrorUtils = {
      getGlobalHandler: () => (err: unknown) => calls.push(['prev', err]),
      setGlobalHandler: (fn: any) => calls.push(['set', fn]),
    };
    (globalThis as any).ErrorUtils = fakeErrorUtils;
    try {
      expect(installCrashReporter()).toBe(true);
      // find the handler that was installed
      const setCall = calls.find(c => c[0] === 'set');
      expect(setCall).toBeDefined();
      const handler = setCall![1];
      handler(new Error('unhandled!'), true);
      expect(getCrashCount()).toBe(1);
      // previous handler chained
      expect(calls.some(c => c[0] === 'prev')).toBe(true);
    } finally {
      delete (globalThis as any).ErrorUtils;
    }
  });
});

// ═══════════════════════════════════════════════════════════
// OBS-004: Sync Alerts (thresholds)
// ═══════════════════════════════════════════════════════════
describe('OBS-004: Sync Alerts', () => {
  beforeEach(() => {
    clearEventBuffer();
    resetAlertState();
  });

  test('no alerts when telemetry is healthy', () => {
    expect(evaluateAlerts({ sync_completed: 5, push_completed: 5, pull_completed: 5 })).toEqual([]);
  });

  test('SYNC_FAILURE_STORM raised at CRITICAL when failures meet threshold', () => {
    const alerts = evaluateAlerts({ sync_failed: DEFAULT_THRESHOLDS.consecutiveSyncFailures });
    expect(alerts.some(a => a.type === 'SYNC_FAILURE_STORM' && a.severity === 'CRITICAL')).toBe(true);
  });

  test('PUSH_FAILURE_RATE raised when push failure ratio breaches', () => {
    const alerts = evaluateAlerts({ push_failed: 6, push_completed: 4 }); // 0.6 >= 0.5
    expect(alerts.some(a => a.type === 'PUSH_FAILURE_RATE')).toBe(true);
  });

  test('PULL_FAILURE_RATE raised when pull failure ratio breaches', () => {
    const alerts = evaluateAlerts({ pull_failed: 5, pull_completed: 3 }); // ~0.625 >= 0.5
    expect(alerts.some(a => a.type === 'PULL_FAILURE_RATE')).toBe(true);
  });

  test('CONFLICT_RATE_HIGH raised when conflict ratio breaches', () => {
    const alerts = evaluateAlerts({ conflict_detected: 3, mutation_applied: 10 }); // 3/13 ~0.23 >= 0.2
    expect(alerts.some(a => a.type === 'CONFLICT_RATE_HIGH')).toBe(true);
  });

  test('QUEUE_BACKLOG raised when queued mutations exceed threshold', () => {
    const alerts = evaluateAlerts({ mutation_queued: DEFAULT_THRESHOLDS.queueBacklog + 1 });
    expect(alerts.some(a => a.type === 'QUEUE_BACKLOG')).toBe(true);
  });

  test('raiseAlerts emits de-duplicated alert events into the metrics stream', () => {
    emitSyncEvent('sync_failed');
    emitSyncEvent('sync_failed');
    emitSyncEvent('sync_failed'); // = threshold 3
    const fresh = raiseAlerts();
    expect(fresh.some(a => a.type === 'SYNC_FAILURE_STORM')).toBe(true);
    // second evaluation is de-duplicated -> no new alerts
    emitSyncEvent('sync_failed');
    const fresh2 = raiseAlerts();
    expect(fresh2.some(a => a.type === 'SYNC_FAILURE_STORM')).toBe(false);
    // but the alert was emitted as a state_changed event the first time
    const events = getRecentEvents();
    expect(events.some(e => e.type === 'state_changed' && e.data?.alert?.type === 'SYNC_FAILURE_STORM')).toBe(true);
  });
});
