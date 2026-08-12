/**
 * G7 Observability — Crash Reporting (OBS-003)
 *
 * Captures unhandled exceptions / JS crashes into a bounded, sanitized crash
 * buffer and exposes a pluggable sink so production can forward crashes to an
 * external reporter (Sentry/Bugsnag/etc.) without changing call sites. Also
 * installs the React Native global error handler when available.
 *
 * Requirements: OBS-003 (Crash Reporting).
 */

import { emitSyncEvent } from './metrics';

export interface CrashReport {
  id: string;
  timestamp: string;
  message: string;
  stack?: string;
  context?: Record<string, any>;
  handled: false;
}

export type CrashSink = (report: CrashReport) => void;

const crashBuffer: CrashReport[] = [];
const MAX_CRASH_BUFFER = 200;
let sink: CrashSink | null = null;
let installed = false;

const SENSITIVE = ['token', 'password', 'secret', 'key', 'authorization', 'email', 'ssn', 'credit'];

function redact(data?: Record<string, any>): Record<string, any> | undefined {
  if (!data) return undefined;
  const out: Record<string, any> = {};
  for (const [k, v] of Object.entries(data)) {
    out[k] = SENSITIVE.some(s => k.toLowerCase().includes(s)) ? '[REDACTED]' : v;
  }
  return out;
}

/**
 * Record a crash. Never throws — sink failures are swallowed so crash reporting
 * can never itself crash the app.
 */
export function recordCrash(error: unknown, context?: Record<string, any>): CrashReport {
  const err = error instanceof Error ? error : new Error(String(error));
  const report: CrashReport = {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
    timestamp: new Date().toISOString(),
    message: err.message,
    stack: err.stack,
    context: redact(context),
    handled: false,
  };
  crashBuffer.push(report);
  if (crashBuffer.length > MAX_CRASH_BUFFER) {
    crashBuffer.splice(0, crashBuffer.length - MAX_CRASH_BUFFER);
  }
  // Surface the crash in the sync metrics event stream (sanitized).
  emitSyncEvent('sync_failed', { crash: report.message });
  try {
    sink?.(report);
  } catch {
    /* a reporter sink failure must never propagate */
  }
  return report;
}

export function getCrashReports(count = 50): CrashReport[] {
  return crashBuffer.slice(-count);
}

export function getCrashCount(): number {
  return crashBuffer.length;
}

export function clearCrashReports(): void {
  crashBuffer.length = 0;
}

/** Plug in an external reporter (production). Pass null to detach. */
export function setCrashReporterSink(next: CrashSink | null): void {
  sink = next;
}

/**
 * Install the global unhandled-exception handler (React Native ErrorUtils).
 * No-op in non-RN environments (e.g. the Node/jest test runner). Idempotent.
 */
export function installCrashReporter(): boolean {
  if (installed) return true;
  const g = globalThis as any;
  const errorUtils = g.ErrorUtils;
  if (errorUtils && typeof errorUtils.setGlobalHandler === 'function') {
    const previous = typeof errorUtils.getGlobalHandler === 'function' ? errorUtils.getGlobalHandler() : null;
    errorUtils.setGlobalHandler((error: unknown, isFatal?: boolean) => {
      recordCrash(error, { isFatal });
      if (typeof previous === 'function') previous(error, isFatal);
    });
    installed = true;
    return true;
  }
  return false;
}
