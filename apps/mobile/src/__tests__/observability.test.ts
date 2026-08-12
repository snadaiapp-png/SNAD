/**
 * G7 Test Suite — Observability & Metrics
 * Tests: Sync event emission, sanitization, summary
 *
 * All tests use the actual metrics.ts API signatures.
 */

import { emitSyncEvent, getRecentEvents, getEventSummary, clearEventBuffer } from '../obs/metrics';

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
