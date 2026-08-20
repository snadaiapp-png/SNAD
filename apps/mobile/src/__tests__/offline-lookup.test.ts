/**
 * G8 — Offline caller lookup (G8-03 §52–§58, §68) + local performance gate
 * (§70: LOCAL_LOOKUP_P95 <= 100 ms on a representative 1k dataset).
 *
 * EXACT / AMBIGUOUS / UNKNOWN / RESTRICTED / INVALID_NUMBER / PRIVATE_NUMBER,
 * stale indicator (STALE != DISABLED), corruption recovery, tenant-switch
 * purge, and the golden offline scenario (network disconnected).
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
        const existing = rows.findIndex(
          (x) =>
            x.tenant_id === _tenantId && x.phone_lookup_token === r.lookupToken &&
            x.entity_type === r.entityType && x.entity_id === r.entityId
        );
        if (existing >= 0) rows.splice(existing, 1);
        rows.push({
          tenant_id: _tenantId,
          phone_lookup_token: r.lookupToken,
          entity_type: r.deleted ? '__TOMBSTONE__' : r.entityType ?? '__TOMBSTONE__',
          entity_id: r.deleted ? '__TOMBSTONE__' : r.entityId ?? r.lookupToken,
          display_name: r.displayName,
          account_name: r.accountName,
          phone_label: r.phoneLabel,
          verified: r.verified ? 1 : 0,
          preferred: r.preferred ? 1 : 0,
          lifecycle_status: r.lifecycleStatus ?? 'ACTIVE',
          privacy_level: r.privacyLevel ?? 'INTERNAL',
          sync_version: r.syncVersion,
          updated_at: r.updatedAt,
          deleted_at: r.deleted ? r.updatedAt : null,
        });
        if (r.deleted) tombstones++;
      }
      return { applied: records.length - tombstones, tombstones };
    }),
    findCallerRows: jest.fn(async (tenantId: string, token: string) => {
      // Mock of the indexed lookup over the in-memory "index".
      return rows
        .filter((r) => r.tenant_id === tenantId && r.phone_lookup_token === token && !r.deleted_at)
        .sort((a, b) => (b.verified - a.verified) || (b.preferred - a.preferred) || a.updated_at.localeCompare(b.updated_at));
    }),
    countCallerEntries: jest.fn(async (tenantId: string) =>
      rows.filter((r) => r.tenant_id === tenantId && !r.deleted_at).length
    ),
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
  };
});

jest.mock('../storage/encryption', () => ({
  encryptField: jest.fn(async (v: string) => v),
  decryptField: jest.fn(async (v: string) => v),
}));

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async () => null),
  setItemAsync: jest.fn(async () => undefined),
  deleteItemAsync: jest.fn(async () => undefined),
}));

import { hmacSha256Hex } from '../caller/hmac';
import {
  METADATA_LAST_SYNCED,
  offlineCallerLookup,
  purgeCallerDataset,
} from '../caller/offline-lookup';
import * as db from '../storage/db';

const TENANT_A = 'tenant-a';
const TENANT_B = 'tenant-b';
const KEY = 'g8-test-tenant-key';
const MOHAMMED = '+966541234567';

function contactRow(token: string, entityId: string, overrides: Record<string, any> = {}) {
  return {
    lookupToken: token,
    entityType: 'CONTACT',
    entityId,
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

beforeEach(() => {
  (db as any).__reset();
});

describe('G8 offline lookup statuses', () => {
  test('golden scenario: online data → dataset → offline EXACT', async () => {
    // Simulated dataset entry produced by the server projection.
    await db.upsertCallerRecords(TENANT_A, [
      contactRow(hmacSha256Hex(KEY, MOHAMMED), 'contact-1'),
    ]);
    await db.setSyncMetadata(METADATA_LAST_SYNCED, new Date().toISOString());

    const result = await offlineCallerLookup({ tenantId: TENANT_A, phone: '0541234567', datasetKey: KEY });

    expect(result.matchStatus).toBe('EXACT');
    expect(result.offline).toBe(true);
    expect(result.displayName).toBe('محمد أحمد');
    expect(result.stale).toBe(false);
  });

  test('two equally ranked contacts → AMBIGUOUS count only', async () => {
    await db.upsertCallerRecords(TENANT_A, [
      contactRow(hmacSha256Hex(KEY, MOHAMMED), 'contact-1'),
      contactRow(hmacSha256Hex(KEY, MOHAMMED), 'contact-2'),
    ]);
    await db.setSyncMetadata(METADATA_LAST_SYNCED, new Date().toISOString());

    const result = await offlineCallerLookup({ tenantId: TENANT_A, phone: MOHAMMED, datasetKey: KEY });

    expect(result.matchStatus).toBe('AMBIGUOUS');
    expect(result.candidateCount).toBe(2);
    expect(result.displayName).toBeUndefined();
  });

  test('wrong key cannot match (UNKNOWN, not a crash)', async () => {
    await db.upsertCallerRecords(TENANT_A, [
      contactRow(hmacSha256Hex(KEY, MOHAMMED), 'contact-1'),
    ]);
    const result = await offlineCallerLookup({
      tenantId: TENANT_A, phone: MOHAMMED, datasetKey: 'different-tenant-key',
    });
    expect(result.matchStatus).toBe('UNKNOWN');
  });

  test('RESTRICTED entry yields RESTRICTED without identity fields', async () => {
    await db.upsertCallerRecords(TENANT_A, [
      contactRow(hmacSha256Hex(KEY, MOHAMMED), 'contact-9', {
        displayName: null, accountName: null, privacyLevel: 'RESTRICTED',
      }),
    ]);
    const result = await offlineCallerLookup({ tenantId: TENANT_A, phone: MOHAMMED, datasetKey: KEY });
    expect(result.matchStatus).toBe('RESTRICTED');
    expect(result.displayName).toBeUndefined();
  });

  test('INVALID_NUMBER and PRIVATE_NUMBER short-circuit', async () => {
    expect((await offlineCallerLookup({ tenantId: TENANT_A, phone: 'garbage', datasetKey: KEY })).matchStatus)
      .toBe('INVALID_NUMBER');
    expect((await offlineCallerLookup({ tenantId: TENANT_A, phone: 'PRIVATE', datasetKey: KEY })).matchStatus)
      .toBe('PRIVATE_NUMBER');
    expect((await offlineCallerLookup({ tenantId: TENANT_A, phone: '  ', datasetKey: KEY })).matchStatus)
      .toBe('INVALID_NUMBER');
  });

  test('missing dataset key ⇒ UNKNOWN + fullResyncSuggested', async () => {
    const result = await offlineCallerLookup({ tenantId: TENANT_A, phone: MOHAMMED, datasetKey: null });
    expect(result.matchStatus).toBe('UNKNOWN');
    expect(result.fullResyncSuggested).toBe(true);
  });

  test('stale dataset still resolves (STALE != DISABLED)', async () => {
    await db.upsertCallerRecords(TENANT_A, [contactRow(hmacSha256Hex(KEY, MOHAMMED), 'contact-1')]);
    await db.setSyncMetadata(METADATA_LAST_SYNCED, new Date(Date.now() - 48 * 3600 * 1000).toISOString());

    const result = await offlineCallerLookup({
      tenantId: TENANT_A, phone: MOHAMMED, datasetKey: KEY,
      staleThresholdMs: 24 * 3600 * 1000,
    });
    expect(result.matchStatus).toBe('EXACT');
    expect(result.stale).toBe(true);
  });

  test('corrupted lookup never crashes — suggests full resync', async () => {
    (db.findCallerRows as jest.Mock).mockRejectedValueOnce(new Error('database disk image is malformed'));
    const result = await offlineCallerLookup({ tenantId: TENANT_A, phone: MOHAMMED, datasetKey: KEY });
    expect(result.matchStatus).toBe('UNKNOWN');
    expect(result.fullResyncSuggested).toBe(true);
  });
});

describe('G8 offline purge', () => {
  test('tenant switch purge: tenant A data is NOT searchable from tenant B', async () => {
    await db.upsertCallerRecords(TENANT_A, [contactRow(hmacSha256Hex(KEY, MOHAMMED), 'contact-1')]);

    // User switches to tenant B → purge A + key.
    await purgeCallerDataset(TENANT_A);

    const result = await offlineCallerLookup({ tenantId: TENANT_A, phone: MOHAMMED, datasetKey: KEY });
    expect(result.matchStatus).toBe('UNKNOWN');
    // Same number on B without any sync must also be unknown (no cross-tenant leak).
    const fromB = await offlineCallerLookup({ tenantId: TENANT_B, phone: MOHAMMED, datasetKey: KEY });
    expect(fromB.matchStatus).toBe('UNKNOWN');
    expect(await db.countCallerEntries(TENANT_A)).toBe(0);
  });
});

describe('G8 LOCAL_LOOKUP_PERFORMANCE (§70)', () => {
  test('P95 local lookup <= 100 ms on a representative 1k dataset', async () => {
    const records = [];
    for (let i = 0; i < 1000; i++) {
      const phone = `+9665${String(i).padStart(8, '0')}`;
      records.push(contactRow(hmacSha256Hex(KEY, phone), `contact-${i}`, {
        displayName: `عميل ${i}`,
        verified: i % 2 === 0,
      }));
    }
    await db.upsertCallerRecords(TENANT_A, records);
    await db.setSyncMetadata(METADATA_LAST_SYNCED, new Date().toISOString());

    const latencies: number[] = [];
    for (let i = 0; i < 100; i++) {
      const phone = `+9665${String(i).padStart(8, '0')}`;
      const start = performance.now();
      const result = await offlineCallerLookup({ tenantId: TENANT_A, phone, datasetKey: KEY });
      const elapsed = performance.now() - start;
      expect(result.matchStatus).toBe('EXACT');
      latencies.push(elapsed);
    }
    latencies.sort((a, b) => a - b);
    const p95 = latencies[Math.floor(latencies.length * 0.95)];
    expect(p95).toBeLessThanOrEqual(100);
  });
});
