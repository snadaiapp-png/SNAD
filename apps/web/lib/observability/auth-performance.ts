export type AuthPerformanceEvent =
  | "login_page_loaded"
  | "login_submitted"
  | "authentication_succeeded"
  | "authentication_failed"
  | "session_restore_started"
  | "session_restored"
  | "session_restore_failed"
  | "workspace_shell_rendered"
  | "workspace_interactive"
  | "workspace_secondary_loaded";

interface AuthMetricPayload {
  event: AuthPerformanceEvent;
  durationMs?: number;
  outcome?: "success" | "failure";
  detail?: string;
}

export function nowMs(): number {
  if (typeof performance === "undefined") return Date.now();
  return performance.now();
}

export function emitAuthMetric(payload: AuthMetricPayload): void {
  if (typeof window === "undefined") return;

  const body = JSON.stringify({
    ...payload,
    pathname: window.location.pathname,
    timestamp: new Date().toISOString(),
    release: process.env.NEXT_PUBLIC_VERCEL_GIT_COMMIT_SHA ?? "local",
  });

  try {
    if (typeof navigator.sendBeacon === "function") {
      navigator.sendBeacon(
        "/api/telemetry/auth",
        new Blob([body], { type: "application/json" }),
      );
      return;
    }
    void fetch("/api/telemetry/auth", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body,
      keepalive: true,
      credentials: "same-origin",
    }).catch(() => undefined);
  } catch {
    // Telemetry must never interrupt authentication.
  }
}
