/**
 * SNAD | سند — Bootstrap Performance Measurement
 *
 * Per PM Directive §1.5: "Measure bootstrap phases using Performance API.
 * Record P50, P95, P99. Add Budget inside CI. Add tests to prevent regression."
 */

export interface BootstrapMeasurement {
  phase: string;
  durationMs: number;
  timestamp: string;
  success: boolean;
}

export interface BootstrapReport {
  measurements: BootstrapMeasurement[];
  totalTimeMs: number;
  p50: number;
  p95: number;
  p99: number;
  timestamp: string;
}

const STORAGE_KEY = "snad_bootstrap_measurements";
const MAX_SAMPLES = 100;

export function markStart(phase: string): void {
  if (typeof performance === "undefined") return;
  performance.mark(`snad-bootstrap-${phase}-start`);
}

export function markEnd(phase: string, success: boolean = true): number {
  if (typeof performance === "undefined") return 0;
  const startMark = `snad-bootstrap-${phase}-start`;
  const endMark = `snad-bootstrap-${phase}-end`;
  try {
    performance.mark(endMark);
    performance.measure(`snad-bootstrap-${phase}`, startMark, endMark);
    const entries = performance.getEntriesByName(`snad-bootstrap-${phase}`);
    const duration = entries.length > 0 ? entries[entries.length - 1].duration : 0;
    recordMeasurement({ phase, durationMs: Math.round(duration), timestamp: new Date().toISOString(), success });
    return Math.round(duration);
  } catch {
    return 0;
  }
}

function recordMeasurement(measurement: BootstrapMeasurement): void {
  if (typeof localStorage === "undefined") return;
  try {
    const existing = JSON.parse(localStorage.getItem(STORAGE_KEY) || "[]");
    existing.push(measurement);
    if (existing.length > MAX_SAMPLES) existing.splice(0, existing.length - MAX_SAMPLES);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(existing));
  } catch { /* ignore */ }
}

export function getMeasurements(): BootstrapMeasurement[] {
  if (typeof localStorage === "undefined") return [];
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY) || "[]"); } catch { return []; }
}

function percentile(values: number[], p: number): number {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.ceil((p / 100) * sorted.length) - 1;
  return sorted[Math.max(0, index)];
}

export function generateReport(): BootstrapReport {
  const measurements = getMeasurements();
  const durations = measurements.map((m) => m.durationMs);
  const totalTime = durations.reduce((sum, d) => sum + d, 0);
  return { measurements, totalTimeMs: totalTime, p50: percentile(durations, 50), p95: percentile(durations, 95), p99: percentile(durations, 99), timestamp: new Date().toISOString() };
}

export function clearMeasurements(): void {
  if (typeof localStorage === "undefined") return;
  try { localStorage.removeItem(STORAGE_KEY); } catch { /* ignore */ }
}

export const BOOTSTRAP_PHASES = {
  LOGIN_PAGE_LOADED: "login_page_loaded",
  LOGIN_FORM_INTERACTIVE: "login_form_interactive",
  LOGIN_SUBMITTED: "login_submitted",
  AUTHENTICATION_STARTED: "authentication_started",
  USER_LOOKUP_COMPLETED: "user_lookup_completed",
  PASSWORD_VERIFICATION_COMPLETED: "password_verification_completed",
  SESSION_CREATED: "session_created",
  TENANT_RESOLVED: "tenant_resolved",
  PERMISSIONS_LOADED: "permissions_loaded",
  WORKSPACE_BOOTSTRAP_STARTED: "workspace_bootstrap_started",
  WORKSPACE_SHELL_RENDERED: "workspace_shell_rendered",
  WORKSPACE_INTERACTIVE: "workspace_interactive",
  WORKSPACE_SECONDARY_DATA_LOADED: "workspace_secondary_data_loaded",
} as const;

export type BootstrapPhase = (typeof BOOTSTRAP_PHASES)[keyof typeof BOOTSTRAP_PHASES];
