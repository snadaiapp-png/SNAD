/**
 * G7 Observability — Sync Metrics & Events
 * Requirements: OBS-001..OBS-006.
 */

export type SyncEventType =
  | 'sync_started'
  | 'sync_completed'
  | 'sync_failed'
  | 'pull_started'
  | 'pull_completed'
  | 'pull_failed'
  | 'push_started'
  | 'push_completed'
  | 'push_failed'
  | 'mutation_queued'
  | 'mutation_applied'
  | 'mutation_rejected'
  | 'conflict_detected'
  | 'conflict_resolved'
  | 'full_resync_started'
  | 'full_resync_completed'
  | 'reauth_required'
  | 'state_changed'
  | 'network_offline'
  | 'storage_warning'
  | 'storage_quota_exceeded'
  | 'cursor_continuity_broken'
  | 'background_sync_started'
  | 'background_sync_stopped';

export interface SyncEvent {
  type: SyncEventType;
  timestamp: string;
  data?: Record<string, any>;
}

export interface SyncDashboardSnapshot {
  generatedAt: string;
  totalEvents: number;
  syncCompleted: number;
  syncFailed: number;
  conflictsDetected: number;
  mutationsQueued: number;
  mutationsRejected: number;
  offlineDetections: number;
  storageWarnings: number;
  storageQuotaExceeded: number;
  cursorContinuityFailures: number;
  successRate: number;
}

const eventBuffer: SyncEvent[] = [];
const MAX_BUFFER_SIZE = 1000;

export function emitSyncEvent(type: SyncEventType, data?: Record<string, any>): void {
  const sanitized = data ? sanitizeEventData(data) : undefined;
  eventBuffer.push({ type, timestamp: new Date().toISOString(), data: sanitized });
  if (eventBuffer.length > MAX_BUFFER_SIZE) {
    eventBuffer.splice(0, eventBuffer.length - MAX_BUFFER_SIZE);
  }
  if (__DEV__) console.log(`[G7] ${type}`, sanitized);
}

export function getRecentEvents(count: number = 50): SyncEvent[] {
  return eventBuffer.slice(-count);
}

export function getEventSummary(): Record<SyncEventType, number> {
  const summary = {} as Record<SyncEventType, number>;
  for (const event of eventBuffer) summary[event.type] = (summary[event.type] ?? 0) + 1;
  return summary;
}

/** OBS-006: stable dashboard projection over the sanitized event stream. */
export function getDashboardSnapshot(): SyncDashboardSnapshot {
  const summary = getEventSummary();
  const completed = summary.sync_completed ?? 0;
  const failed = summary.sync_failed ?? 0;
  const terminal = completed + failed;
  return {
    generatedAt: new Date().toISOString(),
    totalEvents: eventBuffer.length,
    syncCompleted: completed,
    syncFailed: failed,
    conflictsDetected: summary.conflict_detected ?? 0,
    mutationsQueued: summary.mutation_queued ?? 0,
    mutationsRejected: summary.mutation_rejected ?? 0,
    offlineDetections: summary.network_offline ?? 0,
    storageWarnings: summary.storage_warning ?? 0,
    storageQuotaExceeded: summary.storage_quota_exceeded ?? 0,
    cursorContinuityFailures: summary.cursor_continuity_broken ?? 0,
    successRate: terminal === 0 ? 1 : completed / terminal,
  };
}

export function clearEventBuffer(): void {
  eventBuffer.length = 0;
}

function sanitizeEventData(data: Record<string, any>): Record<string, any> {
  const sensitiveKeys = [
    'accessToken', 'refreshToken', 'token', 'password', 'secret',
    'key', 'encryptionKey', 'credentials', 'authorization',
    'email', 'ssn', 'taxId', 'tax_id', 'creditCard', 'credit_card',
  ];
  const sanitized: Record<string, any> = {};
  for (const [key, value] of Object.entries(data)) {
    if (sensitiveKeys.some(sk => key.toLowerCase().includes(sk.toLowerCase()))) {
      sanitized[key] = '[REDACTED]';
    } else if (typeof value === 'object' && value !== null) {
      sanitized[key] = sanitizeEventData(value);
    } else {
      sanitized[key] = value;
    }
  }
  return sanitized;
}

declare const __DEV__: boolean;
