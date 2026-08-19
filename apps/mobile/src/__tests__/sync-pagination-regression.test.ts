/**
 * G7 pagination regression: a multi-page pull must advance the in-memory cursor
 * inside the same sync cycle. Persisting nextCursor alone is not sufficient.
 */

const pullDelta = jest.fn()
  .mockResolvedValueOnce({
    entityType: 'account',
    nextCursor: 'cursor-1',
    entityCount: 1,
    entities: [],
    serverTimestamp: '2026-08-19T00:00:00Z',
    hasMore: true,
  })
  .mockResolvedValueOnce({
    entityType: 'account',
    nextCursor: 'cursor-2',
    entityCount: 0,
    entities: [],
    serverTimestamp: '2026-08-19T00:00:01Z',
    hasMore: false,
  });

jest.mock('../sync/api-client', () => ({
  getApiClient: jest.fn(() => ({
    pullDelta,
    pushBatch: jest.fn(),
    registerDevice: jest.fn().mockResolvedValue({}),
  })),
}));

jest.mock('../storage/db', () => ({
  getSyncMetadata: jest.fn().mockResolvedValue(null),
  setSyncMetadata: jest.fn().mockResolvedValue(undefined),
  upsertEntity: jest.fn().mockResolvedValue(undefined),
  softDeleteEntity: jest.fn().mockResolvedValue(undefined),
  getDatabase: jest.fn(),
}));

jest.mock('../sync/mutation-queue', () => ({
  MutationQueue: jest.fn().mockImplementation(() => ({
    getQueuedMutations: jest.fn().mockResolvedValue([]),
    getMutationsForEntity: jest.fn().mockResolvedValue([]),
  })),
}));

jest.mock('../conflict/resolver', () => ({
  ConflictResolver: jest.fn().mockImplementation(() => ({})),
}));

jest.mock('../obs/metrics', () => ({
  emitSyncEvent: jest.fn(),
}));

jest.mock('../storage/encryption', () => ({
  encryptEntity: jest.fn(async (_type, value) => value),
  decryptEntity: jest.fn(async (_type, value) => value),
}));

import { SyncEngine } from '../sync/sync-engine';

describe('G7 multi-page cursor correctness', () => {
  beforeEach(() => {
    pullDelta.mockClear();
  });

  test('uses response.nextCursor for the next page in the same pull loop', async () => {
    const engine = new SyncEngine({
      apiBaseUrl: 'https://example.invalid',
      deviceId: '00000000-0000-0000-0000-000000000001',
      accessToken: 'token',
      tenantId: '00000000-0000-0000-0000-000000000002',
      userId: '00000000-0000-0000-0000-000000000003',
      pullLimit: 100,
      pushBatchSize: 50,
      clientTimeoutMs: 30000,
      retryDelayMs: 1000,
      maxRetries: 5,
    });

    await (engine as any).pullEntity('account');

    expect(pullDelta).toHaveBeenCalledTimes(2);
    expect(pullDelta.mock.calls[0][1]).toBeNull();
    expect(pullDelta.mock.calls[1][1]).toBe('cursor-1');
  });
});
