/**
 * G7 Local SQLite Storage
 *
 * Requirements: DATA-003 (Local Storage Schema)
 *
 * Implements local persistence for offline CRM data using expo-sqlite.
 * Schema versioning, migrations, and corruption recovery.
 */

import * as SQLite from 'expo-sqlite';
import { EntityType } from '../types';
import { ENTITY_CONFIGS } from '../config/entities';

const DB_NAME = 'snad_g7_offline.db';
const SCHEMA_VERSION = 2;

/** Tombstone sentinel entity values (caller_lookup PK must stay NOT NULL). */
export const CALLER_TOMBSTONE = '__TOMBSTONE__';

let db: SQLite.SQLiteDatabase | null = null;

/**
 * Get or create the database connection.
 */
export async function getDatabase(): Promise<SQLite.SQLiteDatabase> {
  if (!db) {
    db = await SQLite.openDatabaseAsync(DB_NAME);
    await initializeSchema(db);
  }
  return db;
}

/**
 * Initialize the database schema.
 * Creates tables for all entity types + sync metadata.
 */
async function initializeSchema(database: SQLite.SQLiteDatabase): Promise<void> {
  // Check current schema version
  const versionRow = await database.getFirstAsync<{ user_version: number }>(
    'PRAGMA user_version'
  );
  const currentVersion = versionRow?.user_version ?? 0;

  if (currentVersion >= SCHEMA_VERSION) {
    return; // Schema is up to date
  }

  // Run migrations
  await database.execAsync('BEGIN TRANSACTION');

  try {
    if (currentVersion < 1) {
      await migrateToV1(database);
    }
    if (currentVersion < 2) {
      await migrateToV2(database);
    }

    // Update version
    await database.execAsync(`PRAGMA user_version = ${SCHEMA_VERSION}`);
    await database.execAsync('COMMIT');
  } catch (error) {
    await database.execAsync('ROLLBACK');
    throw error;
  }
}

/**
 * Migration V2 (G8-03 §45–§47): offline caller dataset.
 * Purely additive — existing G7 tables are untouched (no wipe on upgrade).
 * PII columns (display_name, account_name) are encrypted at rest by the
 * caller before storage; only the HMAC token is indexed (§48, G8-ADR-004).
 */
async function migrateToV2(database: SQLite.SQLiteDatabase): Promise<void> {
  await database.execAsync(`
    CREATE TABLE IF NOT EXISTS caller_lookup (
      phone_lookup_token TEXT NOT NULL,
      tenant_id TEXT NOT NULL,
      entity_type TEXT NOT NULL,
      entity_id TEXT NOT NULL,
      display_name TEXT,
      account_name TEXT,
      phone_label TEXT,
      verified INTEGER NOT NULL DEFAULT 0,
      preferred INTEGER NOT NULL DEFAULT 0,
      lifecycle_status TEXT NOT NULL,
      privacy_level TEXT NOT NULL,
      sync_version INTEGER NOT NULL,
      updated_at TEXT NOT NULL,
      deleted_at TEXT,
      PRIMARY KEY (tenant_id, phone_lookup_token, entity_type, entity_id)
    );
  `);
  // P0 index: O(log n)-style token lookup, never a scan/decrypt pass (§47).
  await database.execAsync(`
    CREATE INDEX IF NOT EXISTS idx_caller_lookup_token
      ON caller_lookup(tenant_id, phone_lookup_token);
  `);
}

/**
 * Migration V1: Create all entity tables and sync metadata.
 */
async function migrateToV1(database: SQLite.SQLiteDatabase): Promise<void> {
  // Sync metadata table
  await database.execAsync(`
    CREATE TABLE IF NOT EXISTS sync_metadata (
      key TEXT PRIMARY KEY,
      value TEXT NOT NULL,
      updated_at TEXT NOT NULL DEFAULT (datetime('now'))
    );
  `);

  // Mutation queue table
  await database.execAsync(`
    CREATE TABLE IF NOT EXISTS mutation_queue (
      id TEXT PRIMARY KEY,
      idempotency_key TEXT NOT NULL UNIQUE,
      entity_type TEXT NOT NULL,
      entity_id TEXT NOT NULL,
      operation TEXT NOT NULL CHECK (operation IN ('CREATE', 'UPDATE', 'DELETE')),
      expected_version INTEGER,
      payload TEXT NOT NULL,
      client_timestamp TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED', 'SENDING', 'APPLIED', 'REJECTED', 'CONFLICT', 'FAILED')),
      retry_count INTEGER NOT NULL DEFAULT 0,
      max_retries INTEGER NOT NULL DEFAULT 5,
      error_message TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      updated_at TEXT NOT NULL DEFAULT (datetime('now'))
    );
  `);

  await database.execAsync(`
    CREATE INDEX IF NOT EXISTS idx_mutation_queue_status ON mutation_queue(status);
    CREATE INDEX IF NOT EXISTS idx_mutation_queue_entity ON mutation_queue(entity_type, entity_id);
  `);

  // Conflict queue table
  await database.execAsync(`
    CREATE TABLE IF NOT EXISTS conflict_queue (
      id TEXT PRIMARY KEY,
      entity_type TEXT NOT NULL,
      entity_id TEXT NOT NULL,
      conflict_class TEXT NOT NULL,
      conflict_type TEXT NOT NULL,
      client_version INTEGER NOT NULL,
      server_version INTEGER NOT NULL,
      client_state TEXT NOT NULL,
      server_state TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'DEFERRED')),
      resolution TEXT CHECK (resolution IS NULL OR resolution IN ('CLIENT_WINS', 'SERVER_WINS', 'MERGED', 'USER_CHOICE')),
      detected_at TEXT NOT NULL DEFAULT (datetime('now')),
      resolved_at TEXT
    );
  `);

  await database.execAsync(`
    CREATE INDEX IF NOT EXISTS idx_conflict_queue_status ON conflict_queue(status);
    CREATE INDEX IF NOT EXISTS idx_conflict_queue_entity ON conflict_queue(entity_type, entity_id);
  `);

  // Create entity tables
  for (const [type, config] of Object.entries(ENTITY_CONFIGS)) {
    await createEntityTable(database, type as EntityType, config);
  }
}

/**
 * Create entity table with appropriate columns.
 */
async function createEntityTable(
  database: SQLite.SQLiteDatabase,
  type: EntityType,
  config: typeof ENTITY_CONFIGS[EntityType]
): Promise<void> {
  // Base columns common to all entities
  let columns = `
    id TEXT PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    sync_version INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    last_synced_at TEXT,
    deleted_at TEXT
  `;

  // Entity-specific columns
  switch (type) {
    case 'account':
      columns += `,
        name TEXT NOT NULL,
        industry TEXT,
        phone TEXT,
        email TEXT,
        website TEXT,
        address TEXT
      `;
      break;
    case 'contact':
      columns += `,
        account_id TEXT,
        first_name TEXT NOT NULL,
        last_name TEXT NOT NULL,
        email TEXT,
        phone TEXT,
        address TEXT,
        notes TEXT
      `;
      break;
    case 'lead':
      columns += `,
        first_name TEXT NOT NULL,
        last_name TEXT NOT NULL,
        email TEXT,
        phone TEXT,
        status TEXT NOT NULL,
        source TEXT,
        notes TEXT
      `;
      break;
    case 'opportunity':
      columns += `,
        account_id TEXT,
        contact_id TEXT,
        pipeline_id TEXT,
        title TEXT NOT NULL,
        amount REAL,
        stage TEXT NOT NULL,
        close_date TEXT,
        description TEXT
      `;
      break;
    case 'task':
      columns += `,
        title TEXT NOT NULL,
        description TEXT,
        status TEXT NOT NULL,
        due_date TEXT,
        assigned_to TEXT
      `;
      break;
    case 'note':
      columns += `,
        entity_type TEXT NOT NULL,
        entity_id TEXT NOT NULL,
        content TEXT NOT NULL
      `;
      break;
    case 'activity':
      columns += `,
        entity_type TEXT NOT NULL,
        entity_id TEXT NOT NULL,
        activity_type TEXT NOT NULL,
        description TEXT,
        result TEXT
      `;
      break;
  }

  await database.execAsync(`
    CREATE TABLE IF NOT EXISTS ${config.tableName} (
      ${columns}
    );
  `);

  // Create indexes
  await database.execAsync(`
    CREATE INDEX IF NOT EXISTS idx_${config.tableName}_sync_version ON ${config.tableName}(sync_version);
    CREATE INDEX IF NOT EXISTS idx_${config.tableName}_updated_at ON ${config.tableName}(updated_at);
  `);
}

// ============================================================
// CRUD Operations
// ============================================================

/**
 * Upsert an entity (insert or update).
 */
export async function upsertEntity<T extends Record<string, any>>(
  entityType: EntityType,
  entity: T
): Promise<void> {
  const db = await getDatabase();
  const config = ENTITY_CONFIGS[entityType];

  const columns = Object.keys(entity).filter(k => entity[k] !== undefined);
  const placeholders = columns.map(() => '?').join(', ');
  const updateClauses = columns
    .filter(c => c !== 'id' && c !== 'tenant_id')
    .map(c => `${c} = excluded.${c}`)
    .join(', ');

  const values = columns.map(c => entity[c]);

  await db.runAsync(
    `INSERT INTO ${config.tableName} (${columns.join(', ')})
     VALUES (${placeholders})
     ON CONFLICT(id) DO UPDATE SET ${updateClauses}`,
    values
  );
}

/**
 * Get an entity by ID.
 */
export async function getEntity<T>(entityType: EntityType, id: string): Promise<T | null> {
  const db = await getDatabase();
  const config = ENTITY_CONFIGS[entityType];

  const row = await db.getFirstAsync<T>(
    `SELECT * FROM ${config.tableName} WHERE id = ? AND deleted_at IS NULL`,
    id
  );
  return row ?? null;
}

/**
 * Get all entities of a type (with optional limit).
 */
export async function getAllEntities<T>(
  entityType: EntityType,
  limit: number = 1000
): Promise<T[]> {
  const db = await getDatabase();
  const config = ENTITY_CONFIGS[entityType];

  return await db.getAllAsync<T>(
    `SELECT * FROM ${config.tableName} WHERE deleted_at IS NULL LIMIT ?`,
    limit
  );
}

/**
 * Get entities modified since a given sync version.
 */
export async function getEntitiesSince<T>(
  entityType: EntityType,
  sinceVersion: number
): Promise<T[]> {
  const db = await getDatabase();
  const config = ENTITY_CONFIGS[entityType];

  return await db.getAllAsync<T>(
    `SELECT * FROM ${config.tableName} WHERE sync_version > ? AND deleted_at IS NULL`,
    sinceVersion
  );
}

/**
 * Soft-delete an entity.
 */
export async function softDeleteEntity(entityType: EntityType, id: string): Promise<void> {
  const db = await getDatabase();
  const config = ENTITY_CONFIGS[entityType];

  await db.runAsync(
    `UPDATE ${config.tableName} SET deleted_at = datetime('now'), updated_at = datetime('now') WHERE id = ?`,
    id
  );
}

/**
 * Get sync metadata value.
 */
export async function getSyncMetadata(key: string): Promise<string | null> {
  const db = await getDatabase();
  const row = await db.getFirstAsync<{ value: string }>(
    'SELECT value FROM sync_metadata WHERE key = ?',
    key
  );
  return row?.value ?? null;
}

/**
 * Set sync metadata value.
 */
export async function setSyncMetadata(key: string, value: string): Promise<void> {
  const db = await getDatabase();
  await db.runAsync(
    `INSERT INTO sync_metadata (key, value, updated_at) VALUES (?, ?, datetime('now'))
     ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = datetime('now')`,
    key, value
  );
}

// ═══════════════════════════════════════════════════════════
// G8 Caller Dataset (schema v2)
// ═══════════════════════════════════════════════════════════

/**
 * Upsert caller dataset entries idempotently (G8-03 §60): re-applying the
 * same delta never duplicates rows. PII (display/account names) is passed
 * in ALREADY ENCRYPTED form by the sync client.
 */
export async function upsertCallerRecords(
  tenantId: string,
  records: Array<{
    lookupToken: string;
    entityType: string | null;
    entityId: string | null;
    displayName: string | null;
    accountId: string | null;
    accountName: string | null;
    phoneLabel: string | null;
    verified: boolean | null;
    preferred: boolean | null;
    lifecycleStatus: string | null;
    privacyLevel: string | null;
    syncVersion: number;
    updatedAt: string;
    deleted: boolean;
  }>
): Promise<{ applied: number; tombstones: number }> {
  const db = await getDatabase();
  let applied = 0;
  let tombstones = 0;
  await db.withTransactionAsync(async () => {
    for (const record of records) {
      const entityType = record.deleted ? CALLER_TOMBSTONE : (record.entityType ?? CALLER_TOMBSTONE);
      const entityId = record.deleted ? CALLER_TOMBSTONE : (record.entityId ?? record.lookupToken);
      const deletedAt = record.deleted ? record.updatedAt : null;
      await db.runAsync(
        `INSERT INTO caller_lookup (phone_lookup_token, tenant_id, entity_type, entity_id,
            display_name, account_name, phone_label, verified, preferred, lifecycle_status,
            privacy_level, sync_version, updated_at, deleted_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
         ON CONFLICT(tenant_id, phone_lookup_token, entity_type, entity_id) DO UPDATE SET
            display_name = excluded.display_name,
            account_name = excluded.account_name,
            phone_label = excluded.phone_label,
            verified = excluded.verified,
            preferred = excluded.preferred,
            lifecycle_status = excluded.lifecycle_status,
            privacy_level = excluded.privacy_level,
            sync_version = excluded.sync_version,
            updated_at = excluded.updated_at,
            deleted_at = excluded.deleted_at`,
        record.lookupToken, tenantId, entityType, entityId,
        record.displayName, record.accountName, record.phoneLabel,
        record.verified ? 1 : 0, record.preferred ? 1 : 0,
        record.lifecycleStatus ?? 'ACTIVE', record.privacyLevel ?? 'INTERNAL',
        record.syncVersion, record.updatedAt, deletedAt
      );
      if (record.deleted) tombstones++; else applied++;
    }
  });
  return { applied, tombstones };
}

/** Indexed lookup by HMAC token — never a full scan (§47). */
export async function findCallerRows(
  tenantId: string,
  lookupToken: string
): Promise<Array<Record<string, any>>> {
  const db = await getDatabase();
  return await db.getAllAsync<Record<string, any>>(
    `SELECT * FROM caller_lookup
     WHERE tenant_id = ? AND phone_lookup_token = ? AND deleted_at IS NULL
     ORDER BY verified DESC, preferred DESC, updated_at ASC`,
    tenantId, lookupToken
  );
}

/** Count live dataset entries (size visibility, G8-03 §59). */
export async function countCallerEntries(tenantId: string): Promise<number> {
  const db = await getDatabase();
  const row = await db.getFirstAsync<{ n: number }>(
    `SELECT COUNT(*) AS n FROM caller_lookup WHERE tenant_id = ? AND deleted_at IS NULL`,
    tenantId
  );
  return row?.n ?? 0;
}

/**
 * Purge the caller dataset (G8-03 §55–§56 — logout / tenant switch / revoke).
 * Scope: caller_lookup rows only; G7 entity tables are untouched.
 */
export async function purgeCallerDataset(tenantId?: string): Promise<void> {
  const db = await getDatabase();
  if (tenantId) {
    await db.runAsync(`DELETE FROM caller_lookup WHERE tenant_id = ?`, tenantId);
  } else {
    await db.runAsync(`DELETE FROM caller_lookup`);
  }
}
