/**
 * G7 Test Suite — Local Storage Corruption Recovery (OFF-005)
 *
 * Verifies the schema migration in storage/db.ts is transactional: on a
 * mid-migration failure it issues ROLLBACK (so the database is not left in a
 * half-migrated, corrupt state) and does NOT advance PRAGMA user_version; on
 * success it issues COMMIT and advances the version.
 */

jest.mock('expo-sqlite', () => {
  const calls: string[] = [];
  let failOnMetadata = false;
  const db = {
    getFirstAsync: jest.fn().mockResolvedValue({ user_version: 0 }),
    execAsync: jest.fn(async (sql: string) => {
      calls.push(sql);
      if (failOnMetadata && sql.includes('sync_metadata')) {
        throw new Error('simulated disk corruption');
      }
    }),
  };
  return {
    openDatabaseAsync: jest.fn().mockResolvedValue(db),
    __calls: calls,
    __setFail: (v: boolean) => {
      failOnMetadata = v;
    },
    __reset: () => {
      calls.length = 0;
    },
  };
});

import * as SQLite from 'expo-sqlite';
const mock = SQLite as any;

// ═══════════════════════════════════════════════════════════
// OFF-005: Corruption Recovery (transactional migration)
// ═══════════════════════════════════════════════════════════
describe('OFF-005: Corruption Recovery (transactional schema migration)', () => {
  test('mid-migration failure rolls back and leaves user_version unchanged', async () => {
    mock.__reset();
    mock.__setFail(true);

    let getDatabase!: () => Promise<unknown>;
    jest.isolateModules(() => {
      ({ getDatabase } = require('../storage/db'));
    });

    await expect(getDatabase()).rejects.toThrow('simulated disk corruption');

    const calls: string[] = mock.__calls;
    expect(calls).toContain('BEGIN TRANSACTION');
    expect(calls).toContain('ROLLBACK');
    expect(calls).not.toContain('COMMIT');
    // version must NOT be advanced on a failed migration
    expect(calls.some((c: string) => /^PRAGMA user_version\s*=/.test(c))).toBe(false);
  });

  test('successful migration commits and advances user_version', async () => {
    mock.__reset();
    mock.__setFail(false);

    let getDatabase!: () => Promise<unknown>;
    jest.isolateModules(() => {
      ({ getDatabase } = require('../storage/db'));
    });

    await expect(getDatabase()).resolves.toBeDefined();

    const calls: string[] = mock.__calls;
    expect(calls).toContain('BEGIN TRANSACTION');
    expect(calls).toContain('COMMIT');
    expect(calls).not.toContain('ROLLBACK');
    expect(calls.some((c: string) => /^PRAGMA user_version\s*=\s*1/.test(c))).toBe(true);
  });
});
