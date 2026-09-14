/**
 * R2-I auth tests — real login / single-flight refresh / logout
 *
 * Contract under test (backend verified):
 *   POST /api/v1/auth/login   {email, password} → body {accessToken,...}
 *                             + X-SANAD-Refresh-Token response header
 *   POST /api/v1/auth/refresh X-SANAD-Refresh-Token header + {refreshToken} body
 *                             → rotation via X-SANAD-Refresh-Token header
 *   POST /api/v1/auth/logout  bearer → revokes all refresh tokens (204)
 */

import * as SecureStore from 'expo-secure-store';
import { TokenManager, REFRESH_TOKEN_HEADER } from '../auth/token-manager';

jest.mock('expo-secure-store', () => {
  const store = new Map<string, string>();
  return {
    setItemAsync: jest.fn(async (key: string, value: string) => {
      store.set(key, value);
    }),
    getItemAsync: jest.fn(async (key: string) => (store.has(key) ? store.get(key)! : null)),
    deleteItemAsync: jest.fn(async (key: string) => {
      store.delete(key);
    }),
  };
});

const TEST_BASE = 'http://test.local';

interface MockResponseInit {
  status?: number;
  body?: unknown;
  headers?: Record<string, string>;
}

function mockResponse({ status = 200, body = {}, headers = {} }: MockResponseInit = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: {
      get: (name: string): string | null => {
        const entry = Object.entries(headers).find(
          ([key]) => key.toLowerCase() === name.toLowerCase()
        );
        return entry ? entry[1] : null;
      },
    },
    json: async () => body,
    text: async () => JSON.stringify(body),
  };
}

describe('R2-I auth (TokenManager)', () => {
  let fetchMock: jest.Mock;
  let setItemAsync: jest.Mock;
  let deleteItemAsync: jest.Mock;

  beforeEach(() => {
    jest.resetModules();
    process.env.EXPO_PUBLIC_API_BASE_URL = TEST_BASE;
    fetchMock = jest.fn();
    (globalThis as Record<string, unknown>).fetch = fetchMock;
    setItemAsync = SecureStore.setItemAsync as jest.Mock;
    deleteItemAsync = SecureStore.deleteItemAsync as jest.Mock;
    setItemAsync.mockClear();
    deleteItemAsync.mockClear();
  });

  afterEach(() => {
    delete (globalThis as Record<string, unknown>).fetch;
    delete process.env.EXPO_PUBLIC_API_BASE_URL;
  });

  describe('login stores tokens', () => {
    test('POSTs {email, password} and persists access (memory) + refresh (SecureStore)', async () => {
      const tm = new TokenManager();
      fetchMock.mockResolvedValueOnce(
        mockResponse({
          status: 200,
          body: {
            accessToken: 'access-1',
            expiresAt: new Date(Date.now() + 900_000).toISOString(),
          },
          headers: { [REFRESH_TOKEN_HEADER]: 'refresh-1' },
        })
      );

      await tm.login('user@sanad.example', 'secret');

      expect(fetchMock).toHaveBeenCalledTimes(1);
      const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
      expect(url).toBe(`${TEST_BASE}/api/v1/auth/login`);
      expect(init.method).toBe('POST');
      expect(JSON.parse(init.body as string)).toEqual({
        email: 'user@sanad.example',
        password: 'secret',
      });

      // Access token held in memory only
      expect(tm.getAccessToken()).toBe('access-1');
      // Refresh token persisted via SecureStore under the refresh key
      expect(setItemAsync).toHaveBeenCalledWith('g7_refresh_token', 'refresh-1');
      expect(setItemAsync).toHaveBeenCalledWith('g7_refresh_expiry', expect.any(String));
      await expect(tm.getRefreshToken()).resolves.toBe('refresh-1');
    });

    test('invalid credentials (401) reject and store nothing', async () => {
      const tm = new TokenManager();
      fetchMock.mockResolvedValueOnce(mockResponse({ status: 401, body: {} }));

      await expect(tm.login('user@sanad.example', 'wrong')).rejects.toThrow(
        /Invalid email or password/
      );
      expect(tm.getAccessToken()).toBeNull();
      expect(setItemAsync).not.toHaveBeenCalled();
    });

    test('network failure rejects with a clean message and stores nothing', async () => {
      const tm = new TokenManager();
      fetchMock.mockRejectedValueOnce(new Error('ECONNREFUSED'));

      await expect(tm.login('user@sanad.example', 'secret')).rejects.toThrow(/network error/i);
      expect(setItemAsync).not.toHaveBeenCalled();
    });
  });

  describe('refresh single-flight', () => {
    test('concurrent refreshSession calls share ONE HTTP round-trip and persist rotation', async () => {
      const tm = new TokenManager();
      await tm.storeTokens({
        accessToken: 'access-0',
        refreshToken: 'refresh-0',
        expiresIn: 900,
        tokenType: 'Bearer',
      });
      setItemAsync.mockClear();

      fetchMock.mockResolvedValue(
        mockResponse({
          status: 200,
          body: { accessToken: 'access-1' },
          headers: { [REFRESH_TOKEN_HEADER]: 'refresh-1' },
        })
      );

      const [first, second] = await Promise.all([tm.refreshSession(), tm.refreshSession()]);
      expect(first).toBe(true);
      expect(second).toBe(true);

      // Exactly one HTTP refresh for two concurrent callers
      expect(fetchMock).toHaveBeenCalledTimes(1);
      const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
      expect(url).toBe(`${TEST_BASE}/api/v1/auth/refresh`);
      expect(init.method).toBe('POST');
      // Header path (production) AND body path ({refreshToken}, local/dev)
      expect((init.headers as Record<string, string>)[REFRESH_TOKEN_HEADER]).toBe('refresh-0');
      expect(JSON.parse(init.body as string)).toEqual({ refreshToken: 'refresh-0' });

      // New access token active; rotated refresh token persisted
      expect(tm.getAccessToken()).toBe('access-1');
      await expect(tm.getRefreshToken()).resolves.toBe('refresh-1');
      expect(setItemAsync).toHaveBeenCalledWith('g7_refresh_token', 'refresh-1');
    });

    test('sequential refresh after completion issues a NEW round-trip', async () => {
      const tm = new TokenManager();
      await tm.storeTokens({
        accessToken: 'access-0',
        refreshToken: 'refresh-0',
        expiresIn: 900,
        tokenType: 'Bearer',
      });
      fetchMock.mockResolvedValue(
        mockResponse({
          status: 200,
          body: { accessToken: 'access-1' },
          headers: { [REFRESH_TOKEN_HEADER]: 'refresh-1' },
        })
      );

      await expect(tm.refreshSession()).resolves.toBe(true);
      await expect(tm.refreshSession()).resolves.toBe(true);
      expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    test('server rejection returns false (no throw, tokens untouched)', async () => {
      const tm = new TokenManager();
      await tm.storeTokens({
        accessToken: 'access-0',
        refreshToken: 'refresh-0',
        expiresIn: 900,
        tokenType: 'Bearer',
      });
      fetchMock.mockResolvedValueOnce(mockResponse({ status: 401, body: {} }));

      await expect(tm.refreshSession()).resolves.toBe(false);
      await expect(tm.getRefreshToken()).resolves.toBe('refresh-0');
    });

    test('without a stored refresh token returns false without HTTP', async () => {
      const tm = new TokenManager();
      // Ensure the (shared, in-memory) SecureStore mock holds nothing.
      await tm.clearTokens();
      fetchMock.mockClear();

      await expect(tm.refreshSession()).resolves.toBe(false);
      expect(fetchMock).not.toHaveBeenCalled();
    });
  });

  describe('logout clears', () => {
    test('calls POST /auth/logout (bearer) and clears memory + SecureStore', async () => {
      const tm = new TokenManager();
      await tm.storeTokens({
        accessToken: 'access-0',
        refreshToken: 'refresh-0',
        expiresIn: 900,
        tokenType: 'Bearer',
      });
      fetchMock.mockResolvedValueOnce(mockResponse({ status: 204, body: null }));

      await tm.logout();

      expect(fetchMock).toHaveBeenCalledTimes(1);
      const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
      expect(url).toBe(`${TEST_BASE}/api/v1/auth/logout`);
      expect(init.method).toBe('POST');
      expect((init.headers as Record<string, string>).Authorization).toBe('Bearer access-0');

      expect(tm.getAccessToken()).toBeNull();
      await expect(tm.getRefreshToken()).resolves.toBeNull();
      expect(deleteItemAsync).toHaveBeenCalledWith('g7_refresh_token');
      expect(deleteItemAsync).toHaveBeenCalledWith('g7_refresh_expiry');
    });

    test('local session is cleared even when the server is unreachable', async () => {
      const tm = new TokenManager();
      await tm.storeTokens({
        accessToken: 'access-0',
        refreshToken: 'refresh-0',
        expiresIn: 900,
        tokenType: 'Bearer',
      });
      fetchMock.mockRejectedValueOnce(new Error('network down'));

      await expect(tm.logout()).resolves.toBeUndefined();
      expect(tm.getAccessToken()).toBeNull();
      await expect(tm.getRefreshToken()).resolves.toBeNull();
    });
  });
});
