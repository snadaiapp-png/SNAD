export type SyncOperation = {
  id: string;
  tenantId: string;
  entityType: string;
  operation: "CREATE" | "UPDATE" | "ARCHIVE";
  payload: string;
  createdAt: string;
  attempts: number;
};

type WebDatabase = {
  platform: "web-memory";
};

const pendingOperations: SyncOperation[] = [];
const webDatabase: WebDatabase = { platform: "web-memory" };

/**
 * The universal web target intentionally uses memory-only storage.
 * Browser persistence for CRM data requires a separate privacy and encryption
 * decision and must not be enabled implicitly by the mobile workspace.
 */
export async function getMobileDatabase(): Promise<WebDatabase> {
  return webDatabase;
}

export async function enqueueSyncOperation(
  operation: Omit<SyncOperation, "id" | "createdAt" | "attempts">,
): Promise<string> {
  const id = globalThis.crypto.randomUUID();
  pendingOperations.push({
    ...operation,
    id,
    createdAt: new Date().toISOString(),
    attempts: 0,
  });
  return id;
}

export async function listPendingSyncOperations(
  limit = 50,
): Promise<SyncOperation[]> {
  const safeLimit = Math.min(Math.max(limit, 1), 100);
  return pendingOperations.slice(0, safeLimit);
}
