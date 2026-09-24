/**
 * G7 Observability — Sync Alerts (OBS-004)
 *
 * Evaluates sync telemetry against thresholds and raises discrete, de-duplicated
 * alert events when a threshold is crossed, so operators are notified of
 * degraded sync health (failure storms, push/pull failure rates, conflict
 * spikes, queue backlog).
 *
 * Requirements: OBS-004 (Sync Alerts).
 */

import { emitSyncEvent, getEventSummary, type SyncEventType } from './metrics';

export type AlertType =
  | 'SYNC_FAILURE_STORM'
  | 'PUSH_FAILURE_RATE'
  | 'PULL_FAILURE_RATE'
  | 'CONFLICT_RATE_HIGH'
  | 'QUEUE_BACKLOG';

export type AlertSeverity = 'WARNING' | 'CRITICAL';

export interface AlertThresholds {
  /** Number of `sync_failed` events that trips a critical storm alert. */
  consecutiveSyncFailures: number;
  /** push_failed / (push_failed + push_completed) ratio. */
  pushFailureRatio: number;
  /** pull_failed / (pull_failed + pull_completed) ratio. */
  pullFailureRatio: number;
  /** conflict_detected / total mutations ratio. */
  conflictRatio: number;
  /** mutation_queued backlog threshold. */
  queueBacklog: number;
}

export const DEFAULT_THRESHOLDS: AlertThresholds = {
  consecutiveSyncFailures: 3,
  pushFailureRatio: 0.5,
  pullFailureRatio: 0.5,
  conflictRatio: 0.2,
  queueBacklog: 100,
};

export interface SyncAlert {
  type: AlertType;
  severity: AlertSeverity;
  detail: string;
  raisedAt: string;
}

/**
 * Pure evaluation of a telemetry summary against thresholds. Returns the list
 * of alerts currently in breach (no side effects) — easy to unit-test.
 */
export function evaluateAlerts(
  summary: Partial<Record<SyncEventType, number>>,
  thresholds: AlertThresholds = DEFAULT_THRESHOLDS,
): SyncAlert[] {
  const alerts: SyncAlert[] = [];
  const now = new Date().toISOString();

  const syncFailed = summary.sync_failed ?? 0;
  if (syncFailed >= thresholds.consecutiveSyncFailures) {
    alerts.push({
      type: 'SYNC_FAILURE_STORM',
      severity: 'CRITICAL',
      detail: `${syncFailed} sync failures (threshold ${thresholds.consecutiveSyncFailures})`,
      raisedAt: now,
    });
  }

  const pushFailed = summary.push_failed ?? 0;
  const pushCompleted = summary.push_completed ?? 0;
  const pushTotal = pushFailed + pushCompleted;
  if (pushTotal > 0 && pushFailed / pushTotal >= thresholds.pushFailureRatio) {
    alerts.push({
      type: 'PUSH_FAILURE_RATE',
      severity: 'WARNING',
      detail: `push failure ${pushFailed}/${pushTotal}`,
      raisedAt: now,
    });
  }

  const pullFailed = summary.pull_failed ?? 0;
  const pullCompleted = summary.pull_completed ?? 0;
  const pullTotal = pullFailed + pullCompleted;
  if (pullTotal > 0 && pullFailed / pullTotal >= thresholds.pullFailureRatio) {
    alerts.push({
      type: 'PULL_FAILURE_RATE',
      severity: 'WARNING',
      detail: `pull failure ${pullFailed}/${pullTotal}`,
      raisedAt: now,
    });
  }

  const conflicts = summary.conflict_detected ?? 0;
  const mutations =
    (summary.mutation_applied ?? 0) + (summary.mutation_rejected ?? 0) + conflicts;
  if (mutations > 0 && conflicts / mutations >= thresholds.conflictRatio) {
    alerts.push({
      type: 'CONFLICT_RATE_HIGH',
      severity: 'WARNING',
      detail: `${conflicts} conflicts / ${mutations} mutations`,
      raisedAt: now,
    });
  }

  const queued = summary.mutation_queued ?? 0;
  if (queued >= thresholds.queueBacklog) {
    alerts.push({
      type: 'QUEUE_BACKLOG',
      severity: 'WARNING',
      detail: `${queued} queued mutations (threshold ${thresholds.queueBacklog})`,
      raisedAt: now,
    });
  }

  return alerts;
}

/** Tracks alert types already raised in the current breach window (de-dup). */
let raisedKeys = new Set<AlertType>();

/**
 * Evaluate the live event summary and emit one `state_changed` event per
 * newly-raised alert (de-duplicated so a persistent breach does not flood the
 * event buffer). Returns only the freshly-raised alerts.
 */
export function raiseAlerts(thresholds: AlertThresholds = DEFAULT_THRESHOLDS): SyncAlert[] {
  const alerts = evaluateAlerts(getEventSummary(), thresholds);
  const fresh = alerts.filter(a => !raisedKeys.has(a.type));
  for (const a of alerts) raisedKeys.add(a.type);
  for (const a of fresh) {
    emitSyncEvent('state_changed', { alert: a });
  }
  return fresh;
}

/** Reset the de-duplication state (e.g. between sync cycles / in tests). */
export function resetAlertState(): void {
  raisedKeys = new Set<AlertType>();
}
