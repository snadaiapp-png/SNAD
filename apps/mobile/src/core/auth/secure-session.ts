import * as SecureStore from "expo-secure-store";

const REFRESH_TOKEN_KEY = "snad.crm.refresh-token";
const TENANT_ID_KEY = "snad.crm.tenant-id";
const USER_ID_KEY = "snad.crm.user-id";

let accessToken: string | null = null;

export type PersistedSession = {
  refreshToken: string;
  tenantId: string;
  userId: string;
};

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

export async function persistSession(session: PersistedSession): Promise<void> {
  await Promise.all([
    SecureStore.setItemAsync(REFRESH_TOKEN_KEY, session.refreshToken, {
      keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
    }),
    SecureStore.setItemAsync(TENANT_ID_KEY, session.tenantId, {
      keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
    }),
    SecureStore.setItemAsync(USER_ID_KEY, session.userId, {
      keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
    }),
  ]);
}

export async function loadPersistedSession(): Promise<PersistedSession | null> {
  const [refreshToken, tenantId, userId] = await Promise.all([
    SecureStore.getItemAsync(REFRESH_TOKEN_KEY),
    SecureStore.getItemAsync(TENANT_ID_KEY),
    SecureStore.getItemAsync(USER_ID_KEY),
  ]);

  if (!refreshToken || !tenantId || !userId) return null;
  return { refreshToken, tenantId, userId };
}

export async function clearSession(): Promise<void> {
  accessToken = null;
  await Promise.all([
    SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY),
    SecureStore.deleteItemAsync(TENANT_ID_KEY),
    SecureStore.deleteItemAsync(USER_ID_KEY),
  ]);
}
