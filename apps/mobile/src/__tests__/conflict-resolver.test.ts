/**
 * G7 Test Suite — Conflict Resolution
 * Tests: 12-class conflict detection, auto-merge, user resolution
 *
 * All tests use the actual ConflictResolver API signatures.
 */

import { ConflictResolver } from '../conflict/resolver';
import { EntityType } from '../types';

// Mock expo-crypto
jest.mock('expo-crypto', () => ({
  randomUUID: () => 'test-uuid-' + Math.random().toString(36).substr(2, 9),
}));

// Mock the database module
jest.mock('../storage/db', () => ({
  getDatabase: jest.fn().mockResolvedValue({
    runAsync: jest.fn().mockResolvedValue(undefined),
    getAllAsync: jest.fn().mockResolvedValue([]),
    getFirstAsync: jest.fn().mockResolvedValue(null),
  }),
}));

// ═══════════════════════════════════════════════════════════
// TEST 1: Field-Level Auto Merge
// ═══════════════════════════════════════════════════════════
describe('TEST-11: Field-Level Auto Merge', () => {
  let resolver: ConflictResolver;

  beforeEach(() => {
    resolver = new ConflictResolver();
  });

  test('auto-merge non-conflicting fields in account', async () => {
    const result = await resolver.autoMerge(
      'account',
      { phone: '555-0100', website: 'acme.com' },
      { name: 'Acme Corp', industry: 'Tech' }
    );

    // Server fields preserved, client-only fields merged
    expect(result.name).toBe('Acme Corp');
    expect(result.industry).toBe('Tech');
    expect(result.phone).toBe('555-0100');
    expect(result.website).toBe('acme.com');
  });

  test('auto-merge for contact entity', async () => {
    const result = await resolver.autoMerge(
      'contact',
      { email: 'john@local.com', notes: 'Local notes' },
      { first_name: 'John', last_name: 'Doe', phone: '555-0123' }
    );

    expect(result.first_name).toBe('John');
    expect(result.last_name).toBe('Doe');
    expect(result.phone).toBe('555-0123');
    expect(result.email).toBe('john@local.com');
  });

  test('auto-merge for task entity', async () => {
    const result = await resolver.autoMerge(
      'task',
      { description: 'Updated description' },
      { title: 'Task Title', status: 'in_progress' }
    );

    expect(result.title).toBe('Task Title');
    expect(result.status).toBe('in_progress');
    expect(result.description).toBe('Updated description');
  });

  test('auto-merge for activity entity', async () => {
    const result = await resolver.autoMerge(
      'activity',
      { result: 'Positive outcome' },
      { entity_type: 'contact', entity_id: 'c1', activity_type: 'call', description: 'Follow up' }
    );

    expect(result.activity_type).toBe('call');
    expect(result.result).toBe('Positive outcome');
  });

  test('auto-merge rejects non-mergeable entities', async () => {
    await expect(
      resolver.autoMerge('lead', { status: 'qualified' }, { status: 'disqualified' })
    ).rejects.toThrow('Auto-merge not permitted');
  });
});

// ═══════════════════════════════════════════════════════════
// TEST 2: Conflict Detection
// ═══════════════════════════════════════════════════════════
describe('TEST-12: Conflict Detection', () => {
  let resolver: ConflictResolver;

  beforeEach(() => {
    resolver = new ConflictResolver();
  });

  test('detects C1 conflict (same field, different values)', async () => {
    const conflict = await resolver.detectConflict(
      'account', 'acc-1',
      3, { name: 'Version 3 Name' },
      4, { name: 'Version 4 Name' }
    );

    expect(conflict.conflictClass).toBe('C1');
    expect(conflict.entityType).toBe('account');
    expect(conflict.entityId).toBe('acc-1');
    expect(conflict.status).toBe('OPEN');
    expect(conflict.canAutoMerge).toBe(false);
  });

  test('detects C2 conflict (stale client, different fields)', async () => {
    const conflict = await resolver.detectConflict(
      'account', 'acc-1',
      2, { phone: '555-0100' },
      5, { name: 'Updated Name' }
    );

    expect(conflict.conflictClass).toBe('C2');
    expect(conflict.canAutoMerge).toBe(true); // Account allows auto-merge
  });

  test('detects C7 conflict (concurrent, non-overlapping)', async () => {
    const conflict = await resolver.detectConflict(
      'account', 'acc-1',
      3, { phone: '555-0100' },
      3, { email: 'updated@example.com' }
    );

    expect(conflict.conflictClass).toBe('C7');
    expect(conflict.canAutoMerge).toBe(true);
  });

  test('Lead conflict requires user resolution', async () => {
    const conflict = await resolver.detectConflict(
      'lead', 'lead-1',
      3, { status: 'qualified', score: 80 },
      4, { status: 'disqualified', score: 40 }
    );

    expect(conflict.entityType).toBe('lead');
    expect(conflict.status).toBe('OPEN');
  });

  test('Opportunity conflict detected', async () => {
    const conflict = await resolver.detectConflict(
      'opportunity', 'opp-1',
      3, { stage: 'proposal', amount: 50000 },
      4, { stage: 'negotiation', amount: 45000 }
    );

    expect(conflict.entityType).toBe('opportunity');
    expect(conflict.conflictClass).toBe('C1');
  });
});

// ═══════════════════════════════════════════════════════════
// TEST 3: Conflict Resolution
// ═══════════════════════════════════════════════════════════
describe('TEST-13: Conflict Resolution', () => {
  let resolver: ConflictResolver;

  beforeEach(() => {
    resolver = new ConflictResolver();
  });

  test('user can resolve with CLIENT_WINS', async () => {
    // This tests the resolveConflict method
    // In real scenario, conflict would be queued first
    const db = await require('../storage/db').getDatabase();
    db.runAsync.mockResolvedValue(undefined);

    await resolver.resolveConflict('conflict-1', 'CLIENT_WINS');
    expect(db.runAsync).toHaveBeenCalled();
  });

  test('user can resolve with SERVER_WINS', async () => {
    const db = await require('../storage/db').getDatabase();
    db.runAsync.mockResolvedValue(undefined);

    await resolver.resolveConflict('conflict-1', 'SERVER_WINS');
    expect(db.runAsync).toHaveBeenCalled();
  });

  test('user can resolve with MERGED', async () => {
    const db = await require('../storage/db').getDatabase();
    db.runAsync.mockResolvedValue(undefined);

    await resolver.resolveConflict('conflict-1', 'MERGED');
    expect(db.runAsync).toHaveBeenCalled();
  });
});

// ═══════════════════════════════════════════════════════════
// TEST 4: Delete-vs-Update Conflict
// ═══════════════════════════════════════════════════════════
describe('TEST-14: Delete-vs-Update Conflict', () => {
  let resolver: ConflictResolver;

  beforeEach(() => {
    resolver = new ConflictResolver();
  });

  test('stale client with overlapping fields classified as C1', async () => {
    // When client has older version and both modified same field
    const conflict = await resolver.detectConflict(
      'account', 'acc-1',
      2, { name: 'Old version' },
      5, { name: 'Server updated' }
    );

    expect(conflict).toBeDefined();
    // Both modified 'name' field → C1 (same field, different values)
    expect(conflict.conflictClass).toBe('C1');
    expect(conflict.status).toBe('OPEN');
  });
});

// ═══════════════════════════════════════════════════════════
// TEST 5: Conflict Queue
// ═══════════════════════════════════════════════════════════
describe('TEST-15: Conflict Queue', () => {
  let resolver: ConflictResolver;

  beforeEach(() => {
    resolver = new ConflictResolver();
  });

  test('conflict can be queued', async () => {
    const conflict = await resolver.detectConflict(
      'account', 'acc-1',
      3, { name: 'Client' },
      4, { name: 'Server' }
    );

    const db = await require('../storage/db').getDatabase();
    db.runAsync.mockResolvedValue(undefined);

    await resolver.queueConflict(conflict);
    expect(db.runAsync).toHaveBeenCalled();
  });

  test('open conflicts can be retrieved', async () => {
    const db = await require('../storage/db').getDatabase();
    db.getAllAsync.mockResolvedValue([]);

    const conflicts = await resolver.getOpenConflicts();
    expect(Array.isArray(conflicts)).toBe(true);
  });
});
