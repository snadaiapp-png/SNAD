/**
 * G8 — Offline caller dataset sync (G8-03 §37–§44, §60–§61).
 *
 * initial snapshot, delta upsert, tombstone, duplicate delta idempotency,
 * dataset version mismatch ⇒ full rebuild, corrupt cursor recovery, key
 * issuance to SecureStore.
 */

jest.mock('../storage/db', () => {
  const rows: any[] = [];
  const metadata = new Map<string, string>();
  return {
    __rows: rows,
    __metadata: metadata,
    __reset: () => {
      rows.length = 0;
      metadata.clear();
    },
    upsertCallerRecords: jest.fn(async (_tenantId: string, records: any[]) => {
      let tombstones = 0;
      for (const r of records) {
        const key = `${_tenantId}|${r.lookupToken}|${r.entityType ?? '__TOMBSTONE__'}|${r.entityId ?? '__TOMBSTONE__'}`;
        const existing = rows.findIndex((x) => x.__key === key);
        const row = { __key: key, tenant_id: _tenantId, ...r };
        if (existing >= 0) rows.splice(existing, 1);
        rows.push(row);
        if (r.deleted) tombstones++;
      }
      return { applied: records.length - tombstones, tombstones };
    }),
    getSyncMetadata: jest.fn(async (key: string) => metadata.get(key) ?? null),
    setSyncMetadata: jest.fn(async (key: string, value: string) => metadata.set(key, value)),
    purgeCallerDataset: jest.fn(async (tenantId?: string) => {
      if (tenantId) {
        for (let i = rows.length - 1; i >= 0; i--) {
          if (rows[i].tenant_id === tenantId) rows.splice(i, 1);
        }
      } else {
        rows.length = 0;
      }
    }),
    countCallerEntries: jest.fn(async () => rows.length),
  };
});

jest.mock('../storage/encryption', () => ({
  encryptField: jest.fn(async (v: string) => `enc:${v}`),
  decryptField: jest.fn(async (v: string) => String(v).replace(/^enc:/, '')),
}));

jest.mock('expo-secure-store', () => {
  const store = new Map<string, string>();
  return {
    __store: store,
    getItemAsync: jest.fn(async (key: string) => store.get(key) ?? null),
    setItemAsync: jest.fn(async (key: string, value: string) => store.set(key, value)),
    deleteItemAsync: jest.fn(async (key: string) => store.delete(key)),
  };
});

import * as SecureStore from 'expo-secure-store';
import { syncCallerDataset, getDatasetKey } from '../caller/dataset-sync';
import * as db from '../storage/db';

const TENANT = 'tenant-a';
const KEY = 'server-issued-tenant-key';

function page(entries: any[], overrides: Record<string, any> = {}) {
  return {
    datasetVersion: 1,
    fullResyncRequired: false,
    nextCursor: null,
    hasMore: false,
    serverTimestamp: new Date().toISOString(),
    datasetKey: null,
    entries,
    ...overrides,
  };
}

function entry(token: string, overrides: Record<string, any> = {}) {
  return {
    lookupToken: token,
    entityType: 'CONTACT',
    entityId: `entity-${token.slice(0, 6)}`,
    displayName: 'محمد أحمد',
    accountId: null,
    accountName: null,
    phoneLabel: 'Mobile',
    verified: true,
    preferred: false,
    lifecycleStatus: 'ACTIVE',
    privacyLevel: 'INTERNAL',
    syncVersion: 1,
    updatedAt: new Date().toISOString(),
    deleted: false,
    ...overrides,
  };
}

function apiMock(pages: any[], corruptCursor = false) {
  let call = 0;
  return {
    pullCallerDatasetDelta: jest.fn(async (cursor: string | null) => {
      if (corruptCursor && cursor) throw new Error('400 cursor is invalid');
      const p = pages[Math.min(call, pages.length - 1)];
      call++;
      return p;
    }),
  };
}

beforeEach(() => {
  (db as any).__reset();
  (SecureStore as any).__store.clear();
});

describe('G8 caller dataset sync', () => {
  test('initial snapshot issues key once and applies entries', async () => {
    const tokenA = 'token-a';
    const api = apiMock([
      page([entry(tokenA)], { datasetKey: KEY, nextCursor: 'c1', hasMore: false }),
    ]);

    const result = await syncCallerDataset(api, TENANT, true);

    expect(result.applied).toBe(1);
    expect(await getDatasetKey()).toBe(KEY);
    expect((db as any).__rows).toHaveLength(1);
    // PII encrypted before storage.
    expect((db as any).__rows[0].displayName).toBe('enc:محمد أحمد');
    expect((db as any).__metadata.get('caller_dataset_cursor')).toBe('c1');
  });

  test('re-applying the same delta never duplicates rows', async () => {
    const api = apiMock([page([entry('token-a')])]);

    await syncCallerDataset(api, TENANT, true);
    const rowsAfterFirst = (db as any).__rows.length;

    await syncCallerDataset(api, TENANT, false);
    expect((db as any).__rows.length).toBe(rowsAfterFirst);
  });

  test('tombstone replaces a live entry', async () => {
    const tokenA = 'token-a';
    const api = apiMock([
      page([entry(tokenA)]),
      page([entry(tokenA, { deleted: true, displayName: null, accountName: null })]),
    ]);

    await syncCallerDataset(api, TENANT, true);
    await syncCallerDataset(api, TENANT, false);

    const live = (db as any).__rows.filter((r: any) => r.deleted !== true);
    expect(live).toHaveLength(0);
  });

  test('dataset version mismatch triggers full rebuild', async () => {
    const api = apiMock([
      page([entry('token-a')], { datasetVersion: 2, fullResyncRequired: true, datasetKey: KEY }),
      page([entry('token-b')], { datasetVersion: 2, nextCursor: null, hasMore: false }),
    ]);

    const result = await syncCallerDataset(api, TENANT, true);

    expect(result.applied).toBe(1);
    expect((db as any).__rows).toHaveLength(1);
    expect((db as any).__rows[0].lookupToken).toBe('token-b'); // rebuilt from clean state
  });

  test('corrupt cursor is recoverable: marked for full resync, never a permanent crash', async () => {
    const api = apiMock([page([entry('token-a')])], true);
    await db.setSyncMetadata('caller_dataset_cursor', 'corrupt-cursor');

    await expect(syncCallerDataset(api, TENANT, false)).rejects.toThrow('CALLER_DATASET_CORRUPT_CURSOR');
    expect((db as any).__metadata.get('caller_dataset_full_resync_required')).toBe('1');
  });
});
