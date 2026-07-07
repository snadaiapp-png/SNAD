import { NextRequest, NextResponse } from "next/server";

const ALLOWED_EVENTS = new Set([
  "login_page_loaded",
  "login_submitted",
  "authentication_succeeded",
  "authentication_failed",
  "session_restore_started",
  "session_restored",
  "session_restore_failed",
  "workspace_shell_rendered",
  "workspace_interactive",
  "workspace_secondary_loaded",
]);

export async function POST(request: NextRequest) {
  const contentLength = Number(request.headers.get("content-length") ?? 0);
  if (contentLength > 4096) {
    return NextResponse.json({ error: "payload_too_large" }, { status: 413 });
  }

  let payload: Record<string, unknown>;
  try {
    payload = await request.json();
  } catch {
    return NextResponse.json({ error: "invalid_json" }, { status: 400 });
  }

  const event = typeof payload.event === "string" ? payload.event : "";
  if (!ALLOWED_EVENTS.has(event)) {
    return NextResponse.json({ error: "invalid_event" }, { status: 400 });
  }

  const durationMs =
    typeof payload.durationMs === "number" && Number.isFinite(payload.durationMs)
      ? Math.max(0, Math.round(payload.durationMs))
      : undefined;

  console.info(JSON.stringify({
    type: "snad.auth.performance",
    event,
    durationMs,
    outcome: payload.outcome === "failure" ? "failure" : "success",
    pathname: typeof payload.pathname === "string"
      ? payload.pathname.slice(0, 120)
      : undefined,
    release: typeof payload.release === "string"
      ? payload.release.slice(0, 80)
      : undefined,
    timestamp: new Date().toISOString(),
  }));

  return new NextResponse(null, { status: 204 });
}
