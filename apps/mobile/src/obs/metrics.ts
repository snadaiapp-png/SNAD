/**
 * G7 Observability — Sync Metrics & Events
 *
 * Requirements: OBS-001 (Sync Metrics), OBS-002 (Error Tracking),
 *               OBS-003 (Crash Reporting), OBS-004 (Sync Alerts)
 *
 * Collects sync telemetry without exposing sensitive payloads.
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
  | 'state_changed';

export interface SyncEvent {
  type: SyncEventType;
  timestamp: string;
  data?: Record<string, any>;
}

// In-memory event buffer (in production, send to analytics service)
const eventBuffer: SyncEvent[] = [];
const MAX_BUFFER_SIZE = 1000;

/**
 * Emit a sync event.
 * NEVER exposes sensitive payloads (tokens, encryption keys, PII).
 */
export function emitSyncEvent(type: SyncEventType, data?: Record<string, any>): void {
  // Sanitize data — remove sensitive fields
  const sanitized = data ? sanitizeEventData(data) : undefined;

  const event: SyncEvent = {
    type,
    timestamp: new Date().toISOString(),
    data: sanitized,
  };

  eventBuffer.push(event);

  // Trim buffer if too large
  if (eventBuffer.length > MAX_BUFFER_SIZE) {
    eventBuffer.splice(0, eventBuffer.length - MAX_BUFFER_SIZE);
  }

  // Console log for development
  if (__DEV__) {
    console.log(`[G7] ${type}`, sanitized);
  }
}

/**
 * Get recent events (for debugging).
 */
export function getRecentEvents(count: number = 50): SyncEvent[] {
  return eventBuffer.slice(-count);
}

/**
 * Get event summary (for metrics).
 */
export function getEventSummary(): Record<SyncEventType, number> {
  const summary = {} as Record<SyncEventType, number>;
  for (const event of eventBuffer) {
    summary[event.type] = (summary[event.type] ?? 0) + 1;
  }
  return summary;
}

/**
 * Clear event buffer.
 */
export function clearEventBuffer(): void {
  eventBuffer.length = 0;
}

/**
 * Sanitize event data — remove sensitive fields.
 */
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

// Declare __DEV__ for TypeScript
declare const __DEV__: boolean;
