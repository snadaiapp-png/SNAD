import * as Crypto from "expo-crypto";
import * as SQLite from "expo-sqlite";

let databasePromise: Promise<SQLite.SQLiteDatabase> | null = null;

export type SyncOperation = {
  id: string;
  tenantId: string;
  entityType: string;
  operation: "CREATE" | "UPDATE" | "ARCHIVE";
  payload: string;
  createdAt: string;
  attempts: number;
};

export async function getMobileDatabase(): Promise<SQLite.SQLiteDatabase> {
  databasePromise ??= initializeDatabase();
  return databasePromise;
}

async function initializeDatabase(): Promise<SQLite.SQLiteDatabase> {
  const database = await SQLite.openDatabaseAsync("snad-crm-mobile.db");
  await database.execAsync(`
    PRAGMA journal_mode = WAL;
    PRAGMA foreign_keys = ON;

    CREATE TABLE IF NOT EXISTS mobile_metadata (
      key TEXT PRIMARY KEY NOT NULL,
      value TEXT NOT NULL,
      updated_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS mobile_sync_queue (
      id TEXT PRIMARY KEY NOT NULL,
      tenant_id TEXT NOT NULL,
      entity_type TEXT NOT NULL,
      operation TEXT NOT NULL,
      payload TEXT NOT NULL,
      created_at TEXT NOT NULL,
      attempts INTEGER NOT NULL DEFAULT 0,
      last_error TEXT
    );

    CREATE INDEX IF NOT EXISTS idx_mobile_sync_queue_tenant_created
      ON mobile_sync_queue (tenant_id, created_at);
  `);
  return database;
}

export async function enqueueSyncOperation(
  operation: Omit<SyncOperation, "id" | "createdAt" | "attempts">,
): Promise<string> {
  const database = await getMobileDatabase();
  const id = Crypto.randomUUID();
  const createdAt = new Date().toISOString();
  await database.runAsync(
    `INSERT INTO mobile_sync_queue
      (id, tenant_id, entity_type, operation, payload, created_at, attempts)
     VALUES (?, ?, ?, ?, ?, ?, 0)`,
    id,
    operation.tenantId,
    operation.entityType,
    operation.operation,
    operation.payload,
    createdAt,
  );
  return id;
}

export async function listPendingSyncOperations(limit = 50): Promise<SyncOperation[]> {
  const database = await getMobileDatabase();
  const safeLimit = Math.min(Math.max(limit, 1), 100);
  return database.getAllAsync<SyncOperation>(
    `SELECT id,
            tenant_id AS tenantId,
            entity_type AS entityType,
            operation,
            payload,
            created_at AS createdAt,
            attempts
       FROM mobile_sync_queue
      ORDER BY created_at ASC
      LIMIT ?`,
    safeLimit,
  );
}
