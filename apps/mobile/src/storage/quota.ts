/** PERF-002 — local SQLite storage usage measurement. */
import { getDatabase } from './db';

export async function getLocalDatabaseUsageBytes(): Promise<number> {
  const database = await getDatabase();
  const pageCount = await database.getFirstAsync<{ page_count: number }>('PRAGMA page_count');
  const pageSize = await database.getFirstAsync<{ page_size: number }>('PRAGMA page_size');
  const count = Number(pageCount?.page_count ?? 0);
  const size = Number(pageSize?.page_size ?? 0);
  if (!Number.isFinite(count) || !Number.isFinite(size) || count < 0 || size < 0) {
    throw new Error('INVALID_SQLITE_STORAGE_METRICS');
  }
  return count * size;
}
