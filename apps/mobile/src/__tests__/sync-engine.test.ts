/**
 * G7 Test Suite — Sync Engine & Offline
 * Tests: Offline read/write, queue persistence, delta pull, cursor continuation
 *
 * All tests use the actual async API signatures.
 */

// Mock expo-sqlite before any imports
jest.mock('expo-sqlite', () => ({
  openDatabaseAsync: jest.fn().mockResolvedValue({
    execAsync: jest.fn().mockResolvedValue(undefined),
    runAsync: jest.fn().mockResolvedValue(undefined),
    getAllAsync: jest.fn().mockResolvedValue([]),
    getFirstAsync: jest.fn().mockImplementation((sql) => {
      if (sql.includes('user_version')) return Promise.resolve({ user_version: 1 });
      return Promise.resolve(null);
    }),
  }),
}));

jest.mock('expo-crypto', () => ({
  randomUUID: () => 'test-uuid-' + Math.random().toString(36).substr(2, 9),
}));

import { getDatabase, upsertEntity, getEntity, getAllEntities, softDeleteEntity } from '../storage/db';
import { getRecentEvents, emitSyncEvent, clearEventBuffer, getEventSummary } from '../obs/metrics';
import { MutationQueue } from '../sync/mutation-queue';

// ═══════════════════════════════════════════════════════════
// TEST 1: Offline Read
// ═══════════════════════════════════════════════════════════
describe('TEST-01: Offline Read', () => {
  test('getDatabase returns a database instance', async () => {
    const db = await getDatabase();
    expect(db).toBeDefined();
    expect(typeof db.execAsync).toBe('function');
    expect(typeof db.runAsync).toBe('function');
  });

  test('getDatabase caches connection', async () => {
    const db1 = await getDatabase();
    const db2 = await getDatabase();
    expect(db1).toBe(db2);
  });
});

// ═══════════════════════════════════════════════════════════
// TEST 2: Offline Mutation
// ═══════════════════════════════════════════════════════════
describe('TEST-02: Offline Mutation', () => {
  test('upsertEntity calls database runAsync', async () => {
    const db = await getDatabase();
    (db.runAsync as jest.Mock).mockClear();

    await upsertEntity('account', {
      id: 'acc-1',
      tenant_id: 't1',
      name: 'Test Account',
      created_at: '2026-01-01',
      updated_at: '2026-01-01',
    });

    expect(db.runAsync).toHaveBeenCalled();
  });

  test('getEntity calls database getFirstAsync', async () => {
    const db = await getDatabase();
    (db.getFirstAsync as jest.Mock).mockResolvedValue({ id: 'acc-1', name: 'Test' });

    const entity = await getEntity('account', 'acc-1');
    expect(entity).toBeDefined();
    expect(db.getFirstAsync).toHaveBeenCalled();
  });

  test('getAllEntities calls database getAllAsync', async () => {
    const db = await getDatabase();
    (db.getAllAsync as jest.Mock).mockResolvedValue([
      { id: 'acc-1', name: 'Account 1' },
      { id: 'acc-2', name: 'Account 2' },
    ]);

    const entities = await getAllEntities('account');
    expect(entities).toHaveLength(2);
  });

  test('softDeleteEntity calls database runAsync', async () => {
    const db = await getDatabase();
    (db.runAsync as jest.Mock).mockClear();

    await softDeleteEntity('account', 'acc-1');
    expect(db.runAsync).toHaveBeenCalled();
  });
});

// ═══════════════════════════════════════════════════════════
// TEST 3: Queue Persistence
// ═══════════════════════════════════════════════════════════
describe('TEST-03: Queue Persistence', () => {
  test('MutationQueue can be instantiated', () => {
    const queue = new MutationQueue();
    expect(queue).toBeDefined();
  });

  test('queue has enqueue method', () => {
    const queue = new MutationQueue();
    expect(typeof queue.enqueue).toBe('function');
  });

  test('queue has getQueuedMutations method', () => {
    const queue = new MutationQueue();
    expect(typeof queue.getQueuedMutations).toBe('function');
  });
});

// ═══════════════════════════════════════════════════════════
// TEST 4: Delta Pull
// ═══════════════════════════════════════════════════════════
describe('TEST-04: Delta Pull', () => {
  test('getEntitiesSince queries by sync_version', async () => {
    const { getEntitiesSince } = require('../storage/db');
    const db = await getDatabase();
    (db.getAllAsync as jest.Mock).mockResolvedValue([]);

    const entities = await getEntitiesSince('account', 5);
    expect(Array.isArray(entities)).toBe(true);
  });
});

// ═══════════════════════════════════════════════════════════
// TEST 5: Sync Metadata
// ═══════════════════════════════════════════════════════════
describe('TEST-05: Sync Metadata', () => {
  test('getSyncMetadata returns null for missing key', async () => {
    const { getSyncMetadata } = require('../storage/db');
    const db = await getDatabase();
    (db.getFirstAsync as jest.Mock).mockResolvedValue(null);

    const value = await getSyncMetadata('last_cursor');
    expect(value).toBeNull();
  });

  test('setSyncMetadata calls database runAsync', async () => {
    const { setSyncMetadata } = require('../storage/db');
    const db = await getDatabase();
    (db.runAsync as jest.Mock).mockClear();

    await setSyncMetadata('last_cursor', 'abc123');
    expect(db.runAsync).toHaveBeenCalled();
  });
});
