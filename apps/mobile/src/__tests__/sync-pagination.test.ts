/**
 * G7 regression test — cursor-based delta pagination.
 *
 * Requirement: SYNC-008 / full-closure regression gate.
 * The nextCursor returned by page N must be used for page N+1 in the same pull cycle.
 */

jest.mock('../storage/db', () => ({
  getDatabase: jest.fn(),
  getSyncMetadata: jest.fn(),
  setSyncMetadata: jest.fn(),
  upsertEntity: jest.fn(),
}));

jest.mock('../storage/encryption', () => ({
  encryptEntity: jest.fn(async (_entityType: string, value: unknown) => value),
  decryptEntity: jest.fn(async (_entityType: string, value: unknown) => value),
}));

jest.mock('../sync/mutation-queue', () => ({
  MutationQueue: jest.fn().mockImplementation(() => ({
    getMutationsForEntity: jest.fn().mockResolvedValue([]),
    getQueuedMutations: jest.fn().mockResolvedValue([]),
  })),
}));

jest.mock('../conflict/resolver', () => ({
  ConflictResolver: jest.fn().mockImplementation(() => ({})),
}));

jest.mock('../sync/api-client', () => ({
  getApiClient: jest.fn(),
}));

jest.mock('../obs/metrics', () => ({
  emitSyncEvent: jest.fn(),
}));

import { SyncEngine, SyncEngineConfig } from '../sync/sync-engine';
import { getApiClient } from '../sync/api-client';
import { getSyncMetadata, setSyncMetadata } from '../storage/db';

const config: SyncEngineConfig = {
  apiBaseUrl: 'https://example.invalid',
  deviceId: 'device-test',
  accessToken: 'token-test',
  tenantId: 'tenant-test',
  userId: 'user-test',
  pullLimit: 100,
  pushBatchSize: 50,
  clientTimeoutMs: 30_000,
  retryDelayMs: 1_000,
  maxRetries: 5,
};

describe('G7 pagination regression', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (getSyncMetadata as jest.Mock).mockResolvedValue(null);
    (setSyncMetadata as jest.Mock).mockResolvedValue(undefined);
  });

  test('uses response.nextCursor for the next page within the same pull cycle', async () => {
    const pullDelta = jest
      .fn()
      .mockResolvedValueOnce({
        entityType: 'account',
        nextCursor: 'cursor-page-2',
        entityCount: 0,
        entities: [],
        serverTimestamp: '2026-08-20T00:00:00Z',
        hasMore: true,
      })
      .mockResolvedValueOnce({
        entityType: 'account',
        nextCursor: 'cursor-page-3',
        entityCount: 0,
        entities: [],
        serverTimestamp: '2026-08-20T00:00:01Z',
        hasMore: false,
      });

    (getApiClient as jest.Mock).mockReturnValue({
      pullDelta,
      pushBatch: jest.fn(),
    });

    const engine = new SyncEngine(config);
    await (engine as any).pullEntity('account');

    expect(pullDelta).toHaveBeenNthCalledWith(1, 'account', null, 100);
    expect(pullDelta).toHaveBeenNthCalledWith(2, 'account', 'cursor-page-2', 100);
    expect(setSyncMetadata).toHaveBeenNthCalledWith(1, 'cursor:account', 'cursor-page-2');
    expect(setSyncMetadata).toHaveBeenNthCalledWith(2, 'cursor:account', 'cursor-page-3');
  });
});
